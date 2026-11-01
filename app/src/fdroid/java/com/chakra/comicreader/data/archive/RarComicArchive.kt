package com.chakra.comicreader.data.archive

import java.io.File

/**
 * Stub RAR archive implementation for the F-Droid FLOSS build where 7-Zip-JBinding is excluded.
 */
class RarComicArchive(file: File) : ComicArchive {
    init {
        throw UnsupportedComicException("CBR (RAR) archives are omitted in the F-Droid FLOSS build to comply with F-Droid policy. Convert to CBZ (ZIP) or PDF, or download the full release from GitHub.")
    }

    override val pageCount: Int get() = 0
    override fun readPage(index: Int): ByteArray = throw UnsupportedComicException("CBR (RAR) archives are omitted in the F-Droid FLOSS build.")
    override fun pageName(index: Int): String = ""
    override fun close() {}
}
