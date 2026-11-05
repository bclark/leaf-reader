package net.digitalbc.leafreader.data.smb

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream

class SmbTransferTest {
    @Test fun streamsBytesAndReportsProgressWithoutClaimingImportCompletion() {
        val bytes = ByteArray(2 * 1024 * 1024 + 17) { (it % 251).toByte() }
        val events = mutableListOf<SmbTransferProgress>()
        val output = ByteArrayOutputStream()
        copySmbStream(ByteArrayInputStream(bytes), output, bytes.size.toLong(), events::add) { false }
        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(0L, events.first().bytesTransferred)
        assertEquals(bytes.size.toLong(), events.last().bytesTransferred)
        assertEquals(0.95f, SmbImportProgress.fraction(0, 1, events.last(), SmbImportPhase.DOWNLOADING))
    }

    @Test fun unknownLengthIsIndeterminate() {
        val events = mutableListOf<SmbTransferProgress>()
        copySmbStream(ByteArrayInputStream(byteArrayOf(1, 2)), ByteArrayOutputStream(), -1, events::add) { false }
        assertTrue(events.last().isIndeterminate)
        assertEquals(2L, events.last().bytesTransferred)
    }

    @Test fun shortOrGrowingRemoteFileFailsInsteadOfCompleting() {
        for (expected in listOf(1L, 3L)) {
            try {
                copySmbStream(ByteArrayInputStream(byteArrayOf(1, 2)), ByteArrayOutputStream(), expected, {}) { false }
                fail("Expected changed length failure")
            } catch (_: EOFException) { }
        }
    }

    @Test fun cancellationAfterReadDoesNotWriteReturnedChunk() {
        var cancelled = false
        val output = ByteArrayOutputStream()
        val input = object : InputStream() {
            override fun read(): Int = error("bulk read required")
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                bytes[offset] = 1
                cancelled = true
                return 1
            }
        }
        try {
            copySmbStream(input, output, 1, {}) { cancelled }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(0, output.size())
    }

    @Test fun cancellationIsNeverMappedIntoAnOrdinaryConnectionError() {
        val cancellation = CancellationException()
        try { SmbErrors.kind(cancellation); fail("Expected cancellation") }
        catch (actual: CancellationException) { assertSame(cancellation, actual) }
        assertEquals(SmbFailureKind.INVALID_CONFIGURATION, SmbErrors.kind(IllegalArgumentException("private")))
        assertFalse(SmbErrors.message(IllegalArgumentException("private")).contains("private"))
    }
}
