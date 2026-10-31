package com.chakra.comicreader.data.library

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.chakra.comicreader.data.archive.ComicArchiveFactory
import com.chakra.comicreader.data.archive.ComicFormat
import com.chakra.comicreader.data.archive.UnsupportedComicException
import com.chakra.comicreader.data.db.ComicDao
import com.chakra.comicreader.data.db.ComicEntity
import com.chakra.comicreader.data.settings.AppSettings
import com.chakra.comicreader.data.smb.SmbClient
import com.chakra.comicreader.data.smb.SmbEntry
import com.chakra.comicreader.data.smb.SmbTransferProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Owns the comic library: importing files, listing them, tracking progress, and deleting them.
 *
 * Import copies the picked file into app-private storage ([Context.getFilesDir]) so the app has a
 * stable, randomly-accessible copy regardless of the original's lifetime or URI permissions. A
 * cover thumbnail is generated from the first page at import time so the library grid is cheap to
 * render.
 */
class LibraryRepository(
    private val context: Context,
    private val dao: ComicDao,
    private val settings: AppSettings,
) {
    val comics: Flow<List<ComicEntity>> = dao.observeAll()

    suspend fun getComic(id: Long): ComicEntity? = dao.getById(id)

    suspend fun saveProgress(id: Long, page: Int, slot: Int) =
        dao.updateProgress(id, page, slot, System.currentTimeMillis())

    suspend fun setReadingDirection(id: Long, rightToLeft: Boolean) =
        dao.updateReadingDirection(id, rightToLeft)

    /** Finds all supported comic files within a selected folder URI. */
    fun getUrisFromFolder(treeUri: Uri): List<Uri> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val comicExtensions = setOf("cbz", "cbr", "pdf", "zip", "rar")
        return root.listFiles()
            .filter { doc ->
                !doc.isDirectory && doc.name?.substringAfterLast('.', "")?.lowercase() in comicExtensions
            }
            .map { it.uri }
    }

    /** Copies, validates, and indexes a picked comic. Returns the new row id, or an error. */
    suspend fun importComic(
        uri: Uri,
        onProgress: (Float) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Result<Long> = withContext(Dispatchers.IO) {
        val token = UUID.randomUUID().toString()
        val comicsDir = File(context.filesDir, "comics").apply { mkdirs() }
        val coversDir = File(context.filesDir, "covers").apply { mkdirs() }

        val (displayName, totalBytes) = queryFileMeta(uri)
        val name = displayName ?: "Comic"
        val ext = name.substringAfterLast('.', "").lowercase().ifEmpty { "cbz" }
        val title = name.substringBeforeLast('.', name)
        val dest = File(comicsDir, "$token.$ext")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var copied = 0L
                    var read = 0
                    while (!isCancelled() && input.read(buf).also { read = it } >= 0) {
                        if (isCancelled()) break
                        output.write(buf, 0, read)
                        copied += read
                        if (totalBytes > 0L) {
                            onProgress((copied.toFloat() / totalBytes).coerceIn(0f, 1f))
                        }
                    }
                }
            } ?: return@withContext Result.failure(
                UnsupportedComicException("Could not open the selected file."),
            )
            if (isCancelled()) {
                dest.delete()
                return@withContext Result.failure(CancellationException("Import cancelled"))
            }
            indexCopiedFile(dest, coversDir, token, title, source = "local")
        } catch (t: Throwable) {
            dest.delete()
            if (t is CancellationException) throw t
            Log.e(TAG, "Import failed", t)
            Result.failure(t)
        }
    }

    suspend fun listSmbDirectory(relativePath: String): Result<List<SmbEntry>> =
        withContext(Dispatchers.IO) {
            runCatching { SmbClient.listDirectory(settings, relativePath) }
        }

    suspend fun importSmbComic(
        entry: SmbEntry,
        onProgress: (SmbTransferProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Result<Long> = withContext(Dispatchers.IO) {
        val token = UUID.randomUUID().toString()
        val comicsDir = File(context.filesDir, "comics").apply { mkdirs() }
        val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
        val ext = entry.name.substringAfterLast('.', "").lowercase().ifEmpty { "cbz" }
        val title = entry.name.substringBeforeLast('.', entry.name)
        val dest = File(comicsDir, "$token.$ext")

        try {
            SmbClient.downloadToFile(settings, entry, dest, onProgress, isCancelled)
            if (isCancelled()) {
                dest.delete()
                return@withContext Result.failure(CancellationException("SMB import cancelled"))
            }
            indexCopiedFile(dest, coversDir, token, title, source = "smb")
        } catch (t: Throwable) {
            dest.delete()
            if (t is CancellationException) throw t
            Log.e(TAG, "SMB import failed", t)
            Result.failure(t)
        }
    }

    private suspend fun indexCopiedFile(
        dest: File,
        coversDir: File,
        token: String,
        title: String,
        source: String = "local",
    ): Result<Long> {
        val format = ComicArchiveFactory.detectFormat(dest)
        if (format == ComicFormat.UNKNOWN) {
            dest.delete()
            return Result.failure(
                UnsupportedComicException("Not a CBZ, CBR, or PDF file."),
            )
        }

        val (pageCount, coverPath) = ComicArchiveFactory.open(dest).use { archive ->
            val count = archive.pageCount
            if (count == 0) return@use 0 to null
            val coverFile = File(coversDir, "$token.jpg")
            val saved = runCatching { saveCover(archive.readPage(0), coverFile) }
                .getOrDefault(false)
            count to coverFile.absolutePath.takeIf { saved }
        }

        if (pageCount == 0) {
            dest.delete()
            return Result.failure(
                UnsupportedComicException("This archive has no readable pages."),
            )
        }

        val now = System.currentTimeMillis()
        val id = dao.insert(
            ComicEntity(
                title = title,
                filePath = dest.absolutePath,
                coverPath = coverPath,
                format = format.name,
                pageCount = pageCount,
                rightToLeft = settings.defaultRightToLeft,
                dateAdded = now,
                lastOpened = now,
                source = source,
            ),
        )
        return Result.success(id)
    }

    suspend fun deleteComic(id: Long) = withContext(Dispatchers.IO) {
        val comic = dao.getById(id) ?: return@withContext
        runCatching { File(comic.filePath).delete() }
        comic.coverPath?.let { runCatching { File(it).delete() } }
        dao.delete(id)
    }

    private fun saveCover(pageBytes: ByteArray, dest: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(pageBytes, 0, pageBytes.size, bounds)
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longEdge > 0 && longEdge / (sample * 2) >= COVER_MAX_DIMENSION) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = BitmapFactory.decodeByteArray(pageBytes, 0, pageBytes.size, opts)
            ?: return false
        return try {
            dest.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            true
        } finally {
            bitmap.recycle()
        }
    }

    private fun queryFileMeta(uri: Uri): Pair<String?, Long> {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null,
            )
            if (cursor != null && cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                name to size
            } else null to 0L
        } catch (t: Throwable) {
            null to 0L
        } finally {
            cursor?.close()
        }
    }

    companion object {
        private const val TAG = "LibraryRepository"
        private const val COVER_MAX_DIMENSION = 600
    }
}
