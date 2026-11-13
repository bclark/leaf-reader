package net.digitalbc.leafreader.ui.library

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileSizeStateTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun blockedReadLeavesCallerResponsiveAndCancelledResultIsNotPublished() = runBlocking {
        val callerThread = Thread.currentThread()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var staleSize: Long? = null
        var heartbeat = false
        val old = launch(start = CoroutineStart.UNDISPATCHED) {
            staleSize = readFileSize("old") {
                assertNotEquals(callerThread, Thread.currentThread())
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                11L
            }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            withTimeout(3000) { launch { heartbeat = true }.join() }
            assertTrue(heartbeat)
            old.cancel()
            assertEquals(22L, withTimeout(3000) { readFileSize("new") { 22L } })
        } finally {
            release.countDown()
            old.join()
        }
        assertNull(staleSize)
    }

    @Test fun readsActualBytesAndKeepsMissingAndDeniedFilesEmpty() = runBlocking {
        val comic = files.newFile("comic.cbz").apply { writeBytes(ByteArray(2048)) }
        assertEquals(2048L, readFileSize(comic.path))
        assertEquals(0L, readFileSize(files.root.resolve("missing.cbz").path))
        assertEquals(0L, readFileSize("denied") { throw SecurityException("denied") })
        var readCalled = false
        assertEquals(0L, readFileSize(null) { readCalled = true; 1L })
        assertFalse(readCalled)
    }
}
