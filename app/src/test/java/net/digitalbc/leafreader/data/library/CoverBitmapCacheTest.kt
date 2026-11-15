package net.digitalbc.leafreader.data.library

import android.graphics.Bitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class CoverBitmapCacheTest {
    private val key = CoverBitmapCache.Key("https://example.test", "server", "user", 1, "book", "tag", 400)
    private fun bitmap() = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    @Test fun concurrentReadersShareFetchAndOneCancellationKeepsOtherReaderAlive() = runTest {
        val cache = CoverBitmapCache(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        val image = bitmap()
        var calls = 0
        val a = async { cache.get(key) { calls++; gate.await(); image } }
        val b = async { cache.get(key) { calls++; image } }
        runCurrent()
        assertEquals(1, calls)
        a.cancelAndJoin()
        assertFalse(b.isCompleted)
        gate.complete(Unit)
        assertSame(image, b.await())
        assertSame(image, cache.get(key) { calls++; bitmap() })
        assertEquals(1, calls)
    }

    @Test fun lastReaderCancellationCancelsUnderlyingLoad() = runTest {
        val cache = CoverBitmapCache(backgroundScope)
        val cancelled = CompletableDeferred<Unit>()
        val a = async { cache.get(key) { try { awaitCancellation() } finally { cancelled.complete(Unit) } } }
        runCurrent()
        a.cancelAndJoin()
        runCurrent()
        assertTrue(cancelled.isCompleted)
        assertNotNull(cache.get(key) { bitmap() })
    }

    @Test fun bytesBoundEvictionAndScopedKeysDoNotReuseOtherImages() = runTest {
        val image = bitmap()
        val cache = CoverBitmapCache(backgroundScope, image.allocationByteCount)
        var calls = 0
        suspend fun load(k: CoverBitmapCache.Key) = cache.get(k) { calls++; bitmap() }
        load(key)
        load(key.copy(user = "other"))
        load(key)
        assertEquals(3, calls) // Two images exceed the byte budget.
        for (k in listOf(key.copy(url = "https://other.test"), key.copy(server = "other"),
            key.copy(session = 2), key.copy(tag = "new"), key.copy(width = 800))) load(k)
        assertEquals(8, calls)
    }

    @Test fun nullAndFailedLoadsRemainRetryable() = runTest {
        val cache = CoverBitmapCache(backgroundScope)
        assertNull(cache.get(key) { null })
        try { cache.get(key) { error("offline") }; fail("expected failure") } catch (_: IllegalStateException) {}
        assertNotNull(cache.get(key) { bitmap() })
    }

    @Test fun clearRejectsLateNonCancellableResultAndKeepsNewFlight() = runTest {
        val cache = CoverBitmapCache(backgroundScope)
        val gate = CompletableDeferred<Unit>()
        val stale = bitmap()
        val current = bitmap()
        val a = async {
            cache.get(key) { withContext(NonCancellable) { gate.await() }; stale }
        }
        runCurrent()
        cache.clear()
        assertSame(current, cache.get(key) { current })
        gate.complete(Unit)
        a.join()
        assertTrue(a.isCancelled)
        assertSame(current, cache.get(key) { fail("late result replaced cache"); null })
    }
}
