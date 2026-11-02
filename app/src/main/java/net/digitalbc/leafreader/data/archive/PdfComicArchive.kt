package net.digitalbc.leafreader.data.archive

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import net.digitalbc.leafreader.data.page.PageConstants
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * PDF comic archive backed by Android's built-in [PdfRenderer].
 *
 * Each PDF page is rasterized on demand onto a white background at [PageConstants.maxPageDimension]
 * on its long edge so downstream panel detection and reader zoom work identically to CBZ/CBR pages.
 */
class PdfComicArchive(file: File) : ComicArchive, PageSizeSource {

    private val pfd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

    private val renderer: PdfRenderer = try {
        PdfRenderer(pfd)
    } catch (t: Throwable) {
        runCatching { pfd.close() }
        throw t
    }

    override val pageCount: Int get() = renderer.pageCount

    override fun readPage(index: Int): ByteArray = synchronized(this) {
        renderer.openPage(index).use { page ->
            val longEdge = maxOf(page.width, page.height).coerceAtLeast(1)
            val scale = PageConstants.maxPageDimension.toFloat() / longEdge
            val w = (page.width * scale).toInt().coerceAtLeast(1)
            val h = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                out.toByteArray()
            } finally {
                bitmap.recycle()
            }
        }
    }

    override fun pageSize(index: Int): IntArray = synchronized(this) {
        renderer.openPage(index).use { intArrayOf(it.width, it.height) }
    }

    override fun pageName(index: Int): String = "page_${index + 1}.jpg"

    override fun close() {
        synchronized(this) {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
    }
}
