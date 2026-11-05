package net.digitalbc.leafreader.ui.reader

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class SinglePageCurlTest {
    private fun near(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) < 0.001f, "Expected $expected, got $actual")
    }

    @Test
    fun curlStartsFlatAndBothFacesClearAtCompletion() {
        for (fromRight in listOf(true, false)) {
            for (column in 0..36) {
                val u = column / 36f
                near(u * 400f, computeSinglePageCurlX(u, 400f, 0f, fromRight, false))
                for (back in listOf(false, true)) {
                    near(if (fromRight) 0f else 400f,
                        computeSinglePageCurlX(u, 400f, 1f, fromRight, back))
                }
            }
        }
    }

    @Test
    fun facesStayBoundedAndRtlMirrorsLtrThroughoutTurn() {
        for (step in 0..100) {
            for (back in listOf(false, true)) {
                var previous = 0f
                for (column in 0..36) {
                    val u = column / 36f
                    val x = computeSinglePageCurlX(u, 400f, step / 100f, true, back)
                    assertTrue(x.isFinite() && x in 0f..400f)
                    near(400f - x, computeSinglePageCurlX(1f - u, 400f, step / 100f, false, back))
                    // Each face projects monotonically; no mesh folds over itself.
                    if (column > 0) assertTrue(if (back) x <= previous + 0.001f else x >= previous - 0.001f)
                    previous = x
                }
            }
        }
    }

    @Test
    fun progressClampsAndZeroWidthIsSafe() {
        near(computeSinglePageCurlX(0.7f, 400f, 0f, true, false),
            computeSinglePageCurlX(0.7f, 400f, -1f, true, false))
        near(0f, computeSinglePageCurlX(0.7f, 400f, 2f, true, true))
        near(0f, computeSinglePageCurlX(0.7f, 0f, 0.5f, false, true))
    }
}
