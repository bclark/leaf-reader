package net.digitalbc.leafreader.detection

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GutterRefinerTest {

    private fun makeImage(w: Int, h: Int, fill: (x: Int, y: Int) -> Int): ByteArray {
        val buf = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                buf[y * w + x] = fill(x, y).toByte()
            }
        }
        return buf
    }

    @Test
    fun verticalGutterSplitsMergedPanel() {
        // 100x100 image with white page margins (240), content panels (dark=40), and white vertical gutter in 46..54 (240)
        val w = 100
        val h = 100
        val img = makeImage(w, h) { x, y ->
            if (x < 4 || x > 95 || y < 4 || y > 95) 240
            else if (x in 46..54) 240
            else 40
        }

        val refiner = GutterRefiner(img, w, h)
        val initial = listOf(Panel(0.04f, 0.04f, 0.96f, 0.96f))
        val refined = refiner.refine(initial)

        assertEquals(2, refined.size, "Should split 1 merged panel into 2 across the vertical gutter")
        val left = refined[0]
        val right = refined[1]

        assertTrue(left.right in 0.44f..0.48f, "Left panel right edge should end at gutter start ~0.46, was ${left.right}")
        assertTrue(right.left in 0.52f..0.56f, "Right panel left edge should begin after gutter ~0.55, was ${right.left}")
    }

    @Test
    fun horizontalGutterSplitsMergedPanel() {
        val w = 100
        val h = 100
        // Top and bottom content (dark=40), white margins and middle horizontal gutter in 46..54 (240)
        val img = makeImage(w, h) { x, y ->
            if (x < 4 || x > 95 || y < 4 || y > 95) 240
            else if (y in 46..54) 240
            else 40
        }

        val refiner = GutterRefiner(img, w, h)
        val initial = listOf(Panel(0.04f, 0.04f, 0.96f, 0.96f))
        val refined = refiner.refine(initial)

        assertEquals(2, refined.size, "Should split along horizontal gutter")
        val top = refined[0]
        val bottom = refined[1]

        assertTrue(top.bottom in 0.44f..0.48f, "Top panel bottom edge should end at gutter start ~0.46, was ${top.bottom}")
        assertTrue(bottom.top in 0.52f..0.56f, "Bottom panel top edge should start after gutter ~0.55, was ${bottom.top}")
    }

    @Test
    fun snapsEdgesToNearbyGutter() {
        val w = 100
        val h = 100
        // Content on left (40), white gutter at 48..53 (240), content on right (40)
        val img = makeImage(w, h) { x, y ->
            if (x < 4 || x > 95 || y < 4 || y > 95) 240
            else if (x in 48..53) 240
            else 40
        }

        val refiner = GutterRefiner(img, w, h)
        // A panel whose right edge was detected at 0.46 (slightly short of the gutter at 0.48)
        val loosePanel = Panel(0.1f, 0.1f, 0.46f, 0.9f)
        val snapped = refiner.snapEdges(loosePanel)

        assertTrue(
            abs(snapped.right - 0.48f) <= 0.02f,
            "Panel right edge should snap from 0.46 to gutter start at ~0.48, got ${snapped.right}",
        )
    }

    @Test
    fun darkGutterComicIsCorrectlyDetectedAndSplit() {
        val w = 100
        val h = 100
        // Inverted comic: dark margin and gutter (black=20), light content panels (220)
        val img = makeImage(w, h) { x, y ->
            if (x < 4 || x > 95 || y < 4 || y > 95) 20
            else if (x in 46..54) 20
            else 220
        }

        val refiner = GutterRefiner(img, w, h)
        assertTrue(refiner.darkGutters, "Should detect dark gutters on black-margin comic")
        val initial = listOf(Panel(0.04f, 0.04f, 0.96f, 0.96f))
        val refined = refiner.refine(initial)

        assertEquals(2, refined.size, "Should detect dark gutters and split into 2 panels")
    }

    @Test
    fun panelWithNoGutterRemainsSingle() {
        val w = 100
        val h = 100
        // Solid artwork with no clear blank channel across the panel
        val img = makeImage(w, h) { x, y ->
            if (x < 4 || x > 95 || y < 4 || y > 95) 240
            else ((x * 7 + y * 13) % 180) + 30
        }

        val refiner = GutterRefiner(img, w, h)
        val initial = listOf(Panel(0.1f, 0.1f, 0.9f, 0.9f))
        val refined = refiner.refine(initial)

        assertEquals(1, refined.size, "Panel without interior gutters should not be split")
    }
}
