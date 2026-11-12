package net.digitalbc.leafreader.data.library

import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoverImageDecoderTest {
    private fun png(width: Int, height: Int): ByteArray {
        val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use {
            source.compress(Bitmap.CompressFormat.PNG, 100, it)
            source.recycle()
            it.toByteArray()
        }
    }

    @Test fun oversizedServerImageIsBoundedAndRetainedSourceIsUntouched() {
        val bytes = png(1200, 1800) // A server that ignores maxWidth.
        val image = CoverImageDecoder.decode(bytes, 384)!!
        assertEquals(384, image.width)
        assertEquals(576, image.height)
        assertFalse(image.isRecycled)
        val file = File.createTempFile("leaf-cover", ".png")
        try {
            file.writeBytes(bytes)
            val retained = CoverImageDecoder.decodeFile(file.absolutePath, 384)!!
            assertEquals(image.width, retained.width)
            assertEquals(image.height, retained.height)
            assertArrayEquals(bytes, file.readBytes())
        } finally { file.delete() }
    }

    @Test fun tallArtHasABoundedLongEdgeAndSmallArtIsNotUpscaled() {
        val tall = CoverImageDecoder.decode(png(200, 2400), 128)!!
        assertEquals(256, tall.height)
        assertTrue(tall.width in 20..22)
        val small = CoverImageDecoder.decode(png(40, 60), 384)!!
        assertEquals(40, small.width)
        assertEquals(60, small.height)
        assertNull(CoverImageDecoder.decode(byteArrayOf(1, 2, 3), 384))
    }

    @Test fun sizeBucketsDoNotUndersampleAndAvoidPixelByPixelRequestKeys() {
        assertEquals(384, CoverImageDecoder.widthBucket(384))
        assertEquals(384, CoverImageDecoder.widthBucket(360))
        assertEquals(448, CoverImageDecoder.widthBucket(385))
        assertEquals(2048, CoverImageDecoder.widthBucket(Int.MAX_VALUE))
        assertEquals(64, CoverImageDecoder.widthBucket(0))
    }
}
