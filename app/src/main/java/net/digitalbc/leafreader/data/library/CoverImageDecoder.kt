package net.digitalbc.leafreader.data.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.roundToInt

/** Thumbnail-only bounds. Reader/page decoding and source files are unchanged. */
internal object CoverImageDecoder {
    fun widthBucket(widthPx: Int): Int = ((widthPx.coerceIn(1, 2048) + 63) / 64) * 64

    fun decode(bytes: ByteArray, maxWidth: Int): Bitmap? =
        bounded(maxWidth) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }

    fun decodeFile(path: String, maxWidth: Int): Bitmap? =
        bounded(maxWidth) { BitmapFactory.decodeFile(path, it) }

    /** Only call for a privately owned, already bounded thumbnail, before publishing it. */
    fun frontCoverOrOriginal(bitmap: Bitmap): Bitmap {
        if (!WraparoundCover.isCandidate(bitmap.width, bitmap.height)) return bitmap
        val columns = WraparoundCover.columns
        val rows = WraparoundCover.rows
        val samples = IntArray(columns * rows) { index ->
            val pixel = bitmap.getPixel((index % columns * 2 + 1) * bitmap.width / (columns * 2),
                (index / columns * 2 + 1) * bitmap.height / (rows * 2))
            (21 * ((pixel shr 16) and 255) + 72 * ((pixel shr 8) and 255) + 7 * (pixel and 255)) / 100
        }
        val start = WraparoundCover.frontStart(bitmap.width, bitmap.height, samples)
        if (start < 0) return bitmap
        return Bitmap.createBitmap(bitmap, start, 0, bitmap.width - start, bitmap.height)
            .also { if (it !== bitmap) bitmap.recycle() }
    }

    private inline fun bounded(maxWidth: Int, decode: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
        val width = maxWidth.coerceIn(1, 2048)
        val height = width * 2
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        while (options.outWidth / (options.inSampleSize.coerceAtLeast(1) * 2) >= width ||
            options.outHeight / (options.inSampleSize.coerceAtLeast(1) * 2) >= height) {
            options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
        }
        options.inJustDecodeBounds = false
        val bitmap = decode(options) ?: return null
        val scale = minOf(1.0, width.toDouble() / bitmap.width, height.toDouble() / bitmap.height)
        if (scale >= 1.0) return bitmap
        return Bitmap.createScaledBitmap(bitmap,
            (bitmap.width * scale).roundToInt().coerceIn(1, width),
            (bitmap.height * scale).roundToInt().coerceIn(1, height), true)
            .also { if (it !== bitmap) bitmap.recycle() } // Only the unpublished decode is owned here.
    }
}
