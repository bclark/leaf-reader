package com.chakra.comicreader.data.archive

import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * CBR (RAR) archive backed by 7-Zip-JBinding (Full build flavor).
 */
class RarComicArchive(file: File) : ComicArchive {

    private val randomAccessFile = RandomAccessFile(file, "r")
    private val inArchive: IInArchive =
        SevenZip.openInArchive(null, RandomAccessFileInStream(randomAccessFile))

    private val entries: List<Pair<Int, String>> = (0 until inArchive.numberOfItems)
        .mapNotNull { i ->
            val isFolder = inArchive.getProperty(i, PropID.IS_FOLDER) as? Boolean ?: false
            val path = inArchive.getProperty(i, PropID.PATH) as? String ?: return@mapNotNull null
            if (!isFolder && isImageEntry(path)) i to path else null
        }
        .sortedWith(compareBy(NaturalOrderComparator) { it.second })

    override val pageCount: Int get() = entries.size

    override fun readPage(index: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val result = inArchive.extractSlow(entries[index].first, ISequentialOutStream { data ->
            out.write(data)
            data.size
        })
        if (result != ExtractOperationResult.OK) {
            throw IOException("Failed to extract page ${index + 1}: $result")
        }
        return out.toByteArray()
    }

    override fun pageName(index: Int): String = entries[index].second

    override fun close() {
        runCatching { inArchive.close() }
        runCatching { randomAccessFile.close() }
    }
}
