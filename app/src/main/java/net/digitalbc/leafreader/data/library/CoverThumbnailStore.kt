package net.digitalbc.leafreader.data.library

import android.graphics.Bitmap
import android.util.AtomicFile
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.security.MessageDigest

/** Disposable app-private thumbnails only. All file access is serialized on IO, never under the memory-cache lock. */
internal class CoverThumbnailStore(
    private val directory: File,
    private val maxBytes: Long = 128L * 1024 * 1024,
    private val now: () -> Long = System::currentTimeMillis,
) {
    // ponytail: serialize thumbnail IO per process; use a journal only if measured contention warrants it.
    private var epoch = 0L
    @Synchronized fun generation(): Long = epoch

    private fun file(key: CoverBitmapCache.Key): File {
        // No token, URL, username or item name is persisted, including in filenames.
        val identity = listOf("1", key.url, key.server, key.user, key.item, key.tag.orEmpty(), key.width.toString())
            .joinToString("") { "${it.length}:$it" }
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        return File(directory, digest.joinToString("") { "%02x".format(it) } + ".thumb")
    }

    @Synchronized fun read(key: CoverBitmapCache.Key, generation: Long): Bitmap? {
        if (generation != epoch) return null
        val file = file(key)
        return try {
            if (!file.isFile || file.length() !in 9..MAX_ENTRY_BYTES) return null
            val bitmap = DataInputStream(AtomicFile(file).openRead()).use { input ->
                val age = now() - input.readLong()
                val lifetime = if (key.tag.isNullOrBlank()) 60 * 60_000L else 24 * 60 * 60_000L
                if (age !in 0..lifetime) null else CoverImageDecoder.decode(input.readBytes(), key.width)
            }
            if (bitmap == null) file.delete() else {
                file.setLastModified(now())
                CoverPerformanceTrace.event("diskHit")
            }
            bitmap
        } catch (_: Exception) { file.delete(); null }
    }

    @Synchronized fun write(key: CoverBitmapCache.Key, bitmap: Bitmap, generation: Long) {
        if (generation != epoch) return
        val file = AtomicFile(file(key))
        var output: java.io.FileOutputStream? = null
        try {
            if (!directory.isDirectory && !directory.mkdirs()) return
            // An interrupted process may leave a staging file; artwork is disposable.
            directory.listFiles()?.filter { it.name.endsWith(".thumb.new") || it.name.endsWith(".thumb.bak") }
                ?.forEach { it.delete() }
            output = file.startWrite()
            DataOutputStream(output).writeLong(now())
            val limited = object : FilterOutputStream(output) {
                private var bytes = 8L
                override fun write(buffer: ByteArray, offset: Int, length: Int) {
                    if (bytes + length > minOf(MAX_ENTRY_BYTES, maxBytes)) throw IOException("Thumbnail too large")
                    out.write(buffer, offset, length)
                    bytes += length
                }
                override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
            }
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, limited)) throw IOException("Thumbnail encoding failed")
            file.finishWrite(output)
            output = null
            file.baseFile.setLastModified(now())
            trim()
        } catch (_: Exception) {
            output?.let { file.failWrite(it) }
        }
    }

    private fun trim() {
        val files = directory.listFiles()?.filter { it.isFile && it.name.endsWith(".thumb") }.orEmpty()
        var bytes = files.sumOf { it.length() }
        var entries = files.size
        for (file in files.sortedBy { it.lastModified() }) {
            if (bytes <= maxBytes && entries <= 1024) break
            val size = file.length()
            if (file.delete()) { bytes -= size; entries-- }
        }
    }

    @Synchronized fun clear() { epoch++; directory.listFiles()?.forEach { it.delete() } }

    private companion object { const val MAX_ENTRY_BYTES = 4L * 1024 * 1024 }
}
