package net.digitalbc.leafreader.data.archive

/** A comic archive container format. */
enum class ComicFormat { CBZ, CBR, PDF, UNKNOWN }

/**
 * Detects a comic's container format from its leading bytes, falling back to the file extension.
 * Shared by Android and iOS so both route a file the same way — a mislabeled ".cbz" that is really
 * a RAR (common in the wild) is opened as CBR on both platforms, not just one.
 */
object ComicFormatDetector {
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)             // "PK\x03\x04"
    private val ZIP_EMPTY = byteArrayOf(0x50, 0x4B, 0x05, 0x06)             // empty archive
    private val RAR_MAGIC = byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07) // "Rar!\x1a\x07"
    private val PDF_MAGIC = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D)       // "%PDF-"

    /**
     * @param head the first bytes of the file (8 is plenty); fewer is fine, magic just won't match.
     * @param fileExtension the file's extension (without the dot), used only when magic is absent.
     */
    fun detect(head: ByteArray, fileExtension: String): ComicFormat {
        if (head.size >= 4 && (head.startsWith(ZIP_MAGIC) || head.startsWith(ZIP_EMPTY))) return ComicFormat.CBZ
        if (head.size >= 6 && head.startsWith(RAR_MAGIC)) return ComicFormat.CBR
        if (head.size >= 5 && head.startsWith(PDF_MAGIC)) return ComicFormat.PDF
        return when (fileExtension.lowercase()) {
            "cbz", "zip" -> ComicFormat.CBZ
            "cbr", "rar" -> ComicFormat.CBR
            "pdf" -> ComicFormat.PDF
            else -> ComicFormat.UNKNOWN
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }
}
