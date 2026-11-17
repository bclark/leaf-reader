package net.digitalbc.leafreader.ui.library

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.digitalbc.leafreader.data.jellyfin.JellyfinItem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StackCoverHandoffTest {
    @Test fun loadedRearArtworkMovesToFrontWithoutShowingOutgoingCoverOrReloading() = runTest {
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        val composition = Composition(object : AbstractApplier<Unit>(Unit) {
            override fun insertTopDown(index: Int, instance: Unit) = Unit
            override fun insertBottomUp(index: Int, instance: Unit) = Unit
            override fun remove(index: Int, count: Int) = Unit
            override fun move(from: Int, to: Int, count: Int) = Unit
            override fun onClear() = Unit
        }, recomposer)
        val job = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        val colors = mapOf("a" to Color.RED, "b" to Color.BLUE, "c" to Color.GREEN)
        val original = colors.keys.map { StackCover(it, null, null, JellyfinItem(it, it, false)) }
        var covers by mutableStateOf(original)
        var images = emptyMap<String, ImageBitmap?>()
        var requests = 0
        val lateReload = CompletableDeferred<Unit>()
        var frameTime = 0L
        fun frame() {
            Snapshot.sendApplyNotifications()
            runCurrent()
            clock.sendFrame(++frameTime * 16_000_000L)
            runCurrent()
        }
        try {
            composition.setContent {
                images = stackCoverImages(covers, { item ->
                    requests++
                    // The already visible rear cover must be promoted even if a new request stalls.
                    if (requests > 3) lateReload.await()
                    Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(colors.getValue(item.id)) }
                }, 384)
            }
            repeat(3) { frame() }
            assertEquals(3, requests)
            repeat(3) {
                covers = covers.drop(1) + covers.first()
                frame()
                covers.forEach { cover ->
                    assertEquals("Wrong artwork for ${cover.id} after promotion", colors.getValue(cover.id),
                        images[cover.id]?.asAndroidBitmap()?.getPixel(0, 0))
                }
                assertEquals("Promoting retained artwork must not start another load", 3, requests)
            }
        } finally {
            composition.dispose()
            recomposer.close()
            job.cancel()
        }
    }
}
