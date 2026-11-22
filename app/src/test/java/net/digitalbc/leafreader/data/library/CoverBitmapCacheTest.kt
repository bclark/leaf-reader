package net.digitalbc.leafreader.data.library

import android.graphics.Bitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class CoverBitmapCacheTest {
    @get:Rule val files = TemporaryFolder()
    private val key = CoverBitmapCache.Key("https://example.test", "server", "user", 1, "book", "tag", 400)
    private fun bitmap() = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

    @Test fun longTraversalThenReturnDoesNotFetchAnAlreadyLoadedCoverAgain() = runTest {
        val store = CoverThumbnailStore(files.newFolder())
        val cache = CoverBitmapCache(backgroundScope, 2 * bitmap().allocationByteCount, store)
        var fetches = 0
        suspend fun visit(index: Int) = cache.get(key.copy(item = "book-$index")) { fetches++; bitmap() }
        repeat(12) { visit(it) }
        assertEquals(12, fetches)
        assertNotNull(visit(0))
        assertEquals("A disk thumbnail should service a memory-evicted cover", 12, fetches)
        val restarted = CoverBitmapCache(backgroundScope, thumbnails = store)
        assertNotNull(restarted.get(key.copy(item = "book-0", session = 2)) { fetches++; bitmap() })
        assertEquals("A new process/session can reuse a scoped thumbnail without a new fetch", 12, fetches)
        restarted.clear()
        assertNotNull(restarted.get(key.copy(item = "book-0", session = 3)) { fetches++; bitmap() })
        assertEquals("Disconnect/cache invalidation must remove disk copies", 13, fetches)
    }

    @Test fun independentCachedReadsDoNotQueueBehindASlowRead() = runBlocking<Unit> {
        val block = java.util.concurrent.atomic.AtomicBoolean(false)
        val once = java.util.concurrent.atomic.AtomicBoolean(false)
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val store = CoverThumbnailStore(files.newFolder(), now = {
            if (block.get() && once.compareAndSet(false, true)) {
                entered.countDown()
                check(release.await(15, java.util.concurrent.TimeUnit.SECONDS))
            }
            1000L
        })
        val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val warmer = CoverBitmapCache(worker, thumbnails = store)
            warmer.get(key) { bitmap() }
            val other = key.copy(item = "independent-cached-cover")
            warmer.get(other) { bitmap() }
            val cache = CoverBitmapCache(worker, thumbnails = store)
            block.set(true)
            val first = async(Dispatchers.IO) { cache.get(key) { fail("Cached cover fetched again"); null } }
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val second = async(Dispatchers.IO) { cache.get(other) { fail("Cached cover fetched again"); null } }
            assertNotNull("Another cached read must finish while the first read remains held",
                withTimeout(5_000) { second.await() })
            release.countDown()
            assertNotNull(first.await())
        } finally {
            release.countDown()
            worker.coroutineContext[Job]!!.cancelAndJoin()
        }
    }

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
        val store = CoverThumbnailStore(files.newFolder())
        val cache = CoverBitmapCache(backgroundScope, thumbnails = store)
        val gate = CompletableDeferred<Unit>()
        val stale = bitmap().apply { eraseColor(android.graphics.Color.BLUE) }
        val current = bitmap().apply { eraseColor(android.graphics.Color.GREEN) }
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
        assertEquals(android.graphics.Color.GREEN, CoverBitmapCache(backgroundScope, thumbnails = store)
            .get(key) { fail("late clear removed new disk result"); null }!!.getPixel(0, 0))
    }
}
