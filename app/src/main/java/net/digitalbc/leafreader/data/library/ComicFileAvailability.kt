package net.digitalbc.leafreader.data.library

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.digitalbc.leafreader.data.archive.ComicArchiveFactory
import net.digitalbc.leafreader.data.db.ComicEntity

/** Reuses successful browse-time validation; Read still forces archive validation. */
internal class ComicFileAvailability(
    private val validate: (ComicEntity) -> Boolean = { comic ->
        ComicArchiveFactory.open(File(comic.filePath)).use {
            it.pageCount > 0 && it.pageCount == comic.pageCount && it.readPage(0).isNotEmpty()
        }
    },
) {
    private data class Fingerprint(val path: String, val size: Long, val modified: String,
                                   val created: String, val fileKey: String?, val pages: Int)
    private val mutex = Mutex()
    // Serial validation bounds archive IO and deduplicates simultaneous subscribers.
    private val valid = mutableMapOf<Long, Fingerprint>()

    suspend fun retain(ids: Set<Long>) = mutex.withLock { valid.keys.retainAll(ids) }

    suspend fun isReadable(comic: ComicEntity, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val attrs = Files.readAttributes(File(comic.filePath).toPath(), BasicFileAttributes::class.java)
                if (!attrs.isRegularFile || attrs.size() <= 0 || comic.pageCount <= 0) {
                    valid.remove(comic.id)
                    return@withLock false
                }
                val key = Fingerprint(comic.filePath, attrs.size(), attrs.lastModifiedTime().toString(),
                    attrs.creationTime().toString(), attrs.fileKey()?.toString(), comic.pageCount)
                if (!force && valid[comic.id] == key) return@withLock true
                valid.remove(comic.id)
                val result = validate(comic)
                currentCoroutineContext().ensureActive()
                if (result) valid[comic.id] = key
                result
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { valid.remove(comic.id); false }
        }
    }

    suspend fun invalidate() = mutex.withLock { valid.clear() }
}
