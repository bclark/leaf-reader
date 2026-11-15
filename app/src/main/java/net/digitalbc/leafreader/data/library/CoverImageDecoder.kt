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
