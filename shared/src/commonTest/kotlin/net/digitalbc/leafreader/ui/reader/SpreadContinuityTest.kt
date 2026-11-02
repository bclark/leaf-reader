package net.digitalbc.leafreader.ui.reader

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpreadContinuityTest {
    @Test
    fun wideScanResetsPairingWithoutRepeatsAndReverseUsesTheSameUnits() {
        // Human page 6 is wide: cover, 2/3, 4/5, 6, 7/8, 9/10, 11/12.
        val expected = listOf(ReaderSpread(0), ReaderSpread(1, 2), ReaderSpread(3, 4),
            ReaderSpread(5), ReaderSpread(6, 7), ReaderSpread(8, 9), ReaderSpread(10, 11))
        assertSequence(12, setOf(5), expected)
    }

    @Test
    fun portraitBeforeWideAndConsecutiveWideScansRemainStandalone() {
        assertSequence(11, setOf(2, 3, 7), listOf(ReaderSpread(0), ReaderSpread(1),
            ReaderSpread(2), ReaderSpread(3), ReaderSpread(4, 5), ReaderSpread(6),
            ReaderSpread(7), ReaderSpread(8, 9), ReaderSpread(10)))
    }

    @Test
    fun everyOddEvenWidePatternCoversEveryPageExactlyOnceInBothDirections() {
        // Includes empty books, cover-only, even/odd last pages and wide pages on either parity.
        for (count in 0..9) for (mask in 0 until (1 shl count)) {
            val wide = (0 until count).filter { mask and (1 shl it) != 0 }.toSet()
            val forward = mutableListOf<ReaderSpread>()
            var page = 0
            while (page < count) {
                val spread = assertNotNull(resolveReaderSpread(page, count, wide))
                forward += spread
                page = spread.after
            }
            assertEquals((0 until count).toList(), forward.flatMap { listOfNotNull(it.first, it.second) })
            assertSequence(count, wide, forward)
        }
    }

    private fun assertSequence(count: Int, wide: Set<Int>, expected: List<ReaderSpread>) {
        assertNull(resolveReaderSpread(-1, count, wide))
        assertNull(resolveReaderSpread(count, count, wide))
        for ((i, spread) in expected.withIndex()) {
            assertEquals(spread, resolveReaderSpread(spread.first, count, wide))
            spread.second?.let { assertEquals(spread, resolveReaderSpread(it, count, wide)) }
            assertEquals(expected.getOrNull(i + 1), resolveReaderSpread(spread.after, count, wide))
            assertEquals(expected.getOrNull(i - 1), resolveReaderSpread(spread.first - 1, count, wide))
        }
    }

    @Test
    fun postWideMappingUsesActualDestinationInsteadOfParity() {
        for (rtl in listOf(false, true)) {
            val reverse = assertNotNull(computeSpreadTurnMapping(6, 7, 12, rtl, false, ReaderSpread(5)))
            assertEquals(7, reverse.staticPageDocIndex)
            assertEquals(6, reverse.movingFrontDocIndex)
            assertEquals(5, reverse.movingBackDocIndex)
            assertNull(reverse.underneathDocIndex)
            val forward = assertNotNull(computeSpreadTurnMapping(6, 7, 12, rtl, true, ReaderSpread(8, 9)))
            assertEquals(9, forward.underneathDocIndex)
            assertEquals(8, forward.movingBackDocIndex)
            assertEquals(!rtl, forward.isPeelingFromRight)
        }
    }

    @Test
    fun unequalSpreadsReachExactRestingGeometryAndCancellationRestoresSource() {
        val source = computeSpreadDraw(650, 1200, 950, 1200, 1200f, 1080f)
        val target = computeSpreadDraw(950, 1200, 650, 1200, 1200f, 1080f)
        for ((from, to) in listOf(source to target, target to source)) {
            assertEquals(from, lerpSpreadDraw(from, to, 0f))
            assertEquals(to, lerpSpreadDraw(from, to, 1f))
            for (step in 0..100) {
                val t = step / 100f
                val layout = lerpSpreadDraw(from, to, t)
                assertTrue(abs(layout.left1 + layout.width1 - layout.left2) < 0.001f)
                assertTrue(abs(layout.left2 - (from.left2 + (to.left2 - from.left2) * t)) < 0.001f)
            }
            for (right in listOf(false, true)) {
                val width = spreadTurnSheetWidth(from, to, 1f, right)
                val left = computeSpreadCurlFaceX(0f, width, 0f, 0f, to.left2, right, true)
                val edge = computeSpreadCurlFaceX(1f, width, 0f, 0f, to.left2, right, true)
                assertTrue(abs(left - (if (right) to.left1 else to.left2)) < 0.001f)
                assertTrue(abs(edge - (if (right) to.left2 else to.left2 + to.width2)) < 0.001f)
                assertEquals(if (right) from.width2 else from.width1, spreadTurnSheetWidth(from, to, 0f, right))
            }
        }
    }
}
