package net.digitalbc.leafreader.data.archive

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File

/**
 * CBZ (ZIP) archive backed by Apache Commons Compress, which supports random access to entries via
 * [ZipFile] and handles a wider range of ZIP encodings than the JDK's [java.util.zip.ZipFile].
 */
class ZipComicArchive(file: File) : ComicArchive, PageSizeSource {

    private val zip: ZipFile = ZipFile.builder().setFile(file).get()

    private val entries: List<ZipArchiveEntry> = buildList {
        val it = zip.entries
        while (it.hasMoreElements()) {
            val e = it.nextElement()
            if (!e.isDirectory && isImageEntry(e.name)) add(e)
        }
    }.sortedWith(compareBy(NaturalOrderComparator) { it.name })

    override val pageCount: Int get() = entries.size

    override fun readPage(index: Int): ByteArray =
        zip.getInputStream(entries[index]).use { it.readBytes() }

    override fun pageSize(index: Int): IntArray {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        zip.getInputStream(entries[index]).use { android.graphics.BitmapFactory.decodeStream(it, null, options) }
        return intArrayOf(options.outWidth, options.outHeight)
    }

    override fun pageName(index: Int): String = entries[index].name

    override fun readComicInfoXml(): String? {
        val entry = zip.entries.asSequence().firstOrNull {
            it.name.equals("ComicInfo.xml", ignoreCase = true) ||
            it.name.endsWith("/ComicInfo.xml", ignoreCase = true)
        } ?: return null
        return runCatching {
            zip.getInputStream(entry).use { it.readBytes().decodeToString() }
        }.getOrNull()
    }

    override fun close() = zip.close()
}
