package net.digitalbc.leafreader.data.library

import kotlin.test.Test
import kotlin.test.assertEquals

class WraparoundCoverTest {
    private val columns = WraparoundCover.columns
    private val rows = WraparoundCover.rows

    @Test
    fun isolatesFrontFaceAfterDistinctSpineDespiteLettering() {
        val samples = raster { x, y ->
            when {
                x < 45 -> 110 + y % 9
                x >= 50 -> 89 + y % 13
                x == 47 && y in 7..11 -> 180
                else -> 23 + y % 7
            }
        }
        assertEquals(100, WraparoundCover.frontStart(192, 128, samples))
    }

    @Test
    fun isolatesLetteredBindingWithDarkBusyFrontFace() {
        fun cover(withLettering: Boolean) = raster { x, y ->
            when {
                x < 46 -> 91 + y % 11
                x >= 50 -> if (y % 3 == 0) 21 else if (y % 3 == 1) 35 else 90
                x == 48 && y % 2 == 0 && withLettering -> 164
                else -> 33
            }
        }
        assertEquals(100, WraparoundCover.frontStart(192, 128, cover(withLettering = true)))
        assertEquals(-1, WraparoundCover.frontStart(192, 128, cover(withLettering = false)))
    }

    @Test
    fun leavesContinuousLandscapeAndPartialDividerUnchanged() {
        assertEquals(-1, WraparoundCover.frontStart(192, 128, raster { x, y -> x + y + 50 }))
        assertEquals(-1, WraparoundCover.frontStart(192, 128, raster { x, y ->
            if (x in 45..49 && y in 0..14) 15 else 105
        }))
    }

    @Test
    fun leavesPortraitAndWhiteGutterUnchanged() {
        val darkBinding = raster { x, _ -> if (x in 45..49) 15 else 105 }
        assertEquals(-1, WraparoundCover.frontStart(92, 128, darkBinding))
        assertEquals(-1, WraparoundCover.frontStart(192, 128, raster { x, _ ->
            if (x in 45..49) 245 else 95
        }))
    }

    private fun raster(pixel: (x: Int, y: Int) -> Int) =
        IntArray(columns * rows) { pixel(it % columns, it / columns) }
}
