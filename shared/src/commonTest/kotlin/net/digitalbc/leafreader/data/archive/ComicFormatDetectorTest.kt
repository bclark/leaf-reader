package net.digitalbc.leafreader.data.archive

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The shared format-detection logic both platforms route imports through (Android's
 * ComicArchiveFactory and iOS's LibraryStore). Magic bytes win over the extension so a mislabeled
 * file opens the same way on both — the parity-critical case.
 */
class ComicFormatDetectorTest {

    private val zipMagic = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val rarMagic = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07)
    private val pdfMagic = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37)

    @Test
    fun zipMagicIsCbz() {
        assertEquals(ComicFormat.CBZ, ComicFormatDetector.detect(zipMagic, "cbz"))
    }

    @Test
    fun rarMagicIsCbr() {
        assertEquals(ComicFormat.CBR, ComicFormatDetector.detect(rarMagic, "cbr"))
    }

    @Test
    fun pdfMagicIsPdf() {
        assertEquals(ComicFormat.PDF, ComicFormatDetector.detect(pdfMagic, "pdf"))
        assertEquals(ComicFormat.PDF, ComicFormatDetector.detect(pdfMagic, "cbz"))
    }

    @Test
    fun rarBytesUnderCbzExtensionStillCbr() {
        // The case that matters: a .cbz that is really a RAR must convert, not copy in broken.
        assertEquals(ComicFormat.CBR, ComicFormatDetector.detect(rarMagic, "cbz"))
    }

    @Test
    fun zipBytesUnderCbrExtensionStillCbz() {
        assertEquals(ComicFormat.CBZ, ComicFormatDetector.detect(zipMagic, "cbr"))
    }

    @Test
    fun noMagicFallsBackToExtension() {
        val junk = byteArrayOf(0x00, 0x11, 0x22, 0x33)
        assertEquals(ComicFormat.CBR, ComicFormatDetector.detect(junk, "cbr"))
        assertEquals(ComicFormat.CBR, ComicFormatDetector.detect(junk, "rar"))
        assertEquals(ComicFormat.CBZ, ComicFormatDetector.detect(junk, "zip"))
        assertEquals(ComicFormat.CBZ, ComicFormatDetector.detect(junk, "CBZ")) // case-insensitive
        assertEquals(ComicFormat.PDF, ComicFormatDetector.detect(junk, "pdf"))
        assertEquals(ComicFormat.PDF, ComicFormatDetector.detect(junk, "PDF"))
        assertEquals(ComicFormat.UNKNOWN, ComicFormatDetector.detect(junk, "txt"))
    }

    @Test
    fun emptyHeadFallsBackToExtension() {
        assertEquals(ComicFormat.CBR, ComicFormatDetector.detect(ByteArray(0), "cbr"))
        assertEquals(ComicFormat.UNKNOWN, ComicFormatDetector.detect(ByteArray(0), ""))
    }
}
