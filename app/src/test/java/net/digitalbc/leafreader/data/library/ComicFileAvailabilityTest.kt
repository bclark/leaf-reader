package net.digitalbc.leafreader.data.library

import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import net.digitalbc.leafreader.data.db.ComicEntity

class ComicFileAvailabilityTest {
    private fun comic(file: File) = ComicEntity(1, "Book", file.path, null, "CBZ", 3,
        dateAdded = 0, lastOpened = 0)

    @Test fun concurrentObserversAndProgressOnlyChangesReuseValidation() = runBlocking {
        val file = File.createTempFile("leaf-availability", ".cbz")
        try {
            file.writeBytes(byteArrayOf(1, 2, 3))
            var opens = 0
            val cache = ComicFileAvailability({ opens++; true })
            val row = comic(file)
            coroutineScope {
                (1..8).map { async { cache.isReadable(row) } }.forEach { assertTrue(it.await()) }
            }
            assertTrue(cache.isReadable(row.copy(lastPage = 2, lastOpened = 100)))
            assertEquals(1, opens)
            assertTrue(cache.isReadable(row, force = true))
            assertEquals(2, opens) // Reader-time checks are never skipped.
        } finally { file.delete() }
    }

    @Test fun changedDeletedReplacedAndExplicitlyInvalidatedFilesAreCheckedAgain() = runBlocking {
        val file = File.createTempFile("leaf-availability", ".cbz")
        try {
            file.writeBytes(byteArrayOf(1))
            var opens = 0
            val cache = ComicFileAvailability({ opens++; true })
            val row = comic(file)
            assertTrue(cache.isReadable(row))
            file.appendBytes(byteArrayOf(2))
            assertTrue(cache.isReadable(row))
            assertEquals(2, opens)
            cache.invalidate()
            assertTrue(cache.isReadable(row))
            assertEquals(3, opens)
            assertTrue(file.delete())
            assertFalse(cache.isReadable(row))
            file.writeBytes(byteArrayOf(1, 2))
            assertTrue(cache.isReadable(row))
            assertEquals(4, opens)
            cache.retain(emptySet())
            assertTrue(cache.isReadable(row))
            assertEquals(5, opens)
        } finally { file.delete() }
    }

    @Test fun failedValidationIsNotCachedAndForcedReadRejectsSameFingerprintCorruption() = runBlocking {
        val file = File.createTempFile("leaf-availability", ".cbz")
        try {
            file.writeBytes(byteArrayOf(1))
            var valid = false
            val cache = ComicFileAvailability({ valid })
            val row = comic(file)
            assertFalse(cache.isReadable(row))
            valid = true
            assertTrue(cache.isReadable(row))
            valid = false // Unchanged stat values cannot establish integrity.
            assertFalse(cache.isReadable(row, force = true))
            assertFalse(cache.isReadable(row))
        } finally { file.delete() }
    }
}
