package net.digitalbc.leafreader.ui.library

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LocalCoverCacheTest {
    @Test fun decodedBytesBoundActualLocalCacheWithoutRecyclingDisplayedImages() {
        coverBitmapCache.evictAll()
        val displayed = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        val bytes = displayed.allocationByteCount
        val count = coverBitmapCache.maxSize() / bytes + 1
        try {
            coverBitmapCache.put("displayed", displayed.asImageBitmap())
            repeat(count) { index ->
                coverBitmapCache.put("cover-$index", Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888).asImageBitmap())
                assertTrue(coverBitmapCache.size() <= coverBitmapCache.maxSize())
            }
            assertNull(coverBitmapCache["displayed"])
            assertNotNull(coverBitmapCache["cover-${count - 1}"])
            assertFalse("An evicted cover may still be displayed", displayed.isRecycled)
            assertTrue(coverBitmapCache.maxSize() <= 32 * 1024 * 1024)
        } finally { coverBitmapCache.evictAll() }
    }
}
