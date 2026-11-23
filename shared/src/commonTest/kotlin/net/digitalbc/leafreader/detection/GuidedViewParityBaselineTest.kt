package net.digitalbc.leafreader.detection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// See docs/features/GUIDED_VIEW_SYNTHETIC_BASELINE.md for the cross-platform vectors.
class GuidedViewParityBaselineTest {
    private val a = Panel(0.05f, 0.05f, 0.45f, 0.40f)
    private val b = Panel(0.55f, 0.05f, 0.95f, 0.40f)
    private val c = Panel(0.05f, 0.60f, 0.45f, 0.95f)
    private val d = Panel(0.55f, 0.60f, 0.95f, 0.95f)
    private val g = Panel(0.05f, 0.05f, 0.90f, 0.65f)
    private val i = Panel(0.60f, 0.26f, 0.79f, 0.40f)
    private val f = Panel(0.08f, 0.75f, 0.91f, 0.96f)

    @Test
    fun gridReadsAcrossEachRowInTheRequestedDirection() {
        val input = listOf(d, b, c, a)
        assertEquals(listOf(a, b, c, d), PanelOrdering.order(input, rightToLeft = false))
        assertEquals(listOf(b, a, d, c), PanelOrdering.order(input, rightToLeft = true))
    }

    @Test
    fun orderingKeepsADistinctInsetAfterItsBackdrop() {
        assertEquals(listOf(g, i, f), PanelOrdering.order(listOf(f, i, g)))
    }

    @Test
    fun rawReliabilityRejectsMissingSingleAndAmbiguousGeometry() {
        assertFalse(PanelReliability.isReliable(emptyList()))
        assertFalse(PanelReliability.isReliable(listOf(i)))
        assertFalse(PanelReliability.isReliable(listOf(Panel.FULL_PAGE, i)))
        assertTrue(PanelReliability.isReliable(listOf(a, b, c, d)))
    }

    @Test
    fun invalidBoxDoesNotBecomeADetectedPanel() {
        val raw = FloatArray(300 * 6)
        floatArrayOf(0.75f, 0.15f, 0.30f, 0.50f, 0.95f, 0f).copyInto(raw)
        val result = YoloPanelDecoder(inputSize = 640).decode(
            raw, intArrayOf(1, 300, 6), Letterbox.fit(640, 640, 640), 640, 640,
        )
        assertTrue(result.panels.isEmpty())
    }
}
