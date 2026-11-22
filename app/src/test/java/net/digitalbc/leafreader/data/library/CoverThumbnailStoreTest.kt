package net.digitalbc.leafreader.data.library

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class CoverThumbnailStoreTest {
    @get:Rule val files = TemporaryFolder()
    private val key = CoverBitmapCache.Key("https://private.test", "server", "user", 1, "comic", "v1", 64)
    private fun bitmap() = Bitmap.createBitmap(16, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }

    @Test fun thumbnailsSurviveRecreationButNeverCrossIdentityTagOrSize() = runBlocking<Unit> {
        val directory = files.newFolder()
        val store = CoverThumbnailStore(directory)
        store.write(key, bitmap(), store.generation())
        assertEquals(Color.MAGENTA, CoverThumbnailStore(directory).read(key.copy(session = 4), 0)!!.getPixel(0, 0))
        for (other in listOf(key.copy(url = "https://other.test"), key.copy(server = "other"),
            key.copy(user = "other"), key.copy(item = "other"), key.copy(tag = "v2"), key.copy(width = 128))) {
            assertNull(store.read(other, store.generation()))
        }
        assertTrue(directory.listFiles()!!.all { it.name.matches(Regex("[0-9a-f]{64}\\.thumb")) })
        val contents = directory.listFiles()!!.single().readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(contents.contains("private.test"))
        assertFalse(contents.contains("comic"))
    }

    @Test fun diskBytesStayBoundedAndLeastRecentlyUsedEntryIsEvicted() = runBlocking<Unit> {
        val directory = files.newFolder()
        var clock = 1000L
        val measure = CoverThumbnailStore(directory, now = { clock })
        measure.write(key, bitmap(), 0)
        val entryBytes = directory.listFiles()!!.single().length()
        measure.clear()
        val store = CoverThumbnailStore(directory, maxBytes = entryBytes * 2, now = { clock })
        store.write(key, bitmap(), 0); clock++
        store.write(key.copy(item = "b"), bitmap(), 0); clock++
        assertNotNull(store.read(key, 0)); clock++
        store.write(key.copy(item = "c"), bitmap(), 0)
        assertTrue(directory.listFiles()!!.sumOf { it.length() } <= entryBytes * 2)
        assertNull(store.read(key.copy(item = "b"), 0))
        assertNotNull(store.read(key, 0))
    }

    @Test fun tinyFilesAndInterruptedWritesCannotAccumulateUnboundedMetadata() = runBlocking<Unit> {
        val directory = files.newFolder()
        repeat(1030) { java.io.File(directory, it.toString().padStart(64, '0') + ".thumb").writeBytes(byteArrayOf(1)) }
        val abandoned = java.io.File(directory, "orphan.thumb.new").apply { writeBytes(ByteArray(100)) }
        val store = CoverThumbnailStore(directory)
        store.write(key, bitmap(), 0)
        assertTrue(directory.listFiles()!!.size <= 1024)
        assertFalse(abandoned.exists())
        assertNotNull(store.read(key, 0))
    }

    @Test fun cachedReadConcurrencyIsBoundedAndQueuedCancellationDoesNotConsumeAPermit() = runBlocking<Unit> {
        val block = java.util.concurrent.atomic.AtomicBoolean(false)
        val entered = java.util.concurrent.CountDownLatch(2)
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val release = java.util.concurrent.CountDownLatch(1)
        val store = CoverThumbnailStore(files.newFolder(), now = {
            if (block.get()) {
                calls.incrementAndGet()
                entered.countDown()
                check(release.await(10, java.util.concurrent.TimeUnit.SECONDS))
            }
            1000L
        })
        val keys = (0..2).map { key.copy(item = "cached-$it") }
        keys.forEach { store.write(it, bitmap(), 0) }
        val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            block.set(true)
            val first = worker.async { store.read(keys[0], 0) }
            val second = worker.async { store.read(keys[1], 0) }
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            val queued = async(start = CoroutineStart.UNDISPATCHED) { store.read(keys[2], 0) }
            assertTrue("Third read must queue rather than allocate another decode", queued.isActive)
            queued.cancelAndJoin()
            assertEquals(2, calls.get())
            block.set(false)
            release.countDown()
            assertNotNull(first.await()); assertNotNull(second.await())
            assertNotNull(store.read(keys[2], 0))
        } finally {
            block.set(false); release.countDown()
            worker.coroutineContext[Job]!!.cancelAndJoin()
        }
    }

    @Test fun staleCorruptOrUnavailableStorageFallsThroughAndClearRejectsOldWrites() = runBlocking<Unit> {
        val directory = files.newFolder()
        var clock = 1000L
        val store = CoverThumbnailStore(directory, now = { clock })
        store.write(key.copy(tag = null), bitmap(), 0)
        clock += 60 * 60_000L + 1
        assertNull(store.read(key.copy(tag = null), 0))
        store.write(key, bitmap(), 0)
        directory.listFiles()!!.single().writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9))
        assertNull(store.read(key, 0))
        assertTrue(directory.listFiles()!!.isEmpty())
        store.write(key, bitmap(), 0)
        clock += 24 * 60 * 60_000L + 1
        assertNull(store.read(key, 0))
        store.clear()
        store.write(key, bitmap(), 0)
        assertTrue(directory.listFiles()!!.isEmpty())
        val unavailable = CoverThumbnailStore(files.newFile())
        unavailable.write(key, bitmap(), 0)
        assertNull(unavailable.read(key, 0))
        val tiny = CoverThumbnailStore(directory, maxBytes = 8)
        tiny.write(key, bitmap(), 0)
        assertTrue(directory.listFiles()!!.none { it.name.endsWith(".thumb") })
    }
}
