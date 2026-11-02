package net.digitalbc.leafreader.ui.reader

import net.digitalbc.leafreader.detection.Panel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CameraTransformTest {

    private fun assertNear(expected: Float, actual: Float, tolerance: Float = 0.001f) {
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, got $actual")
    }

    @Test
    fun fullPageContainFitCentersThePage() {
        // 1000x1500 page in a 500x500 container, fill = 1: height limits, scale = 1/3.
        val draw = computePageDraw(Panel.FULL_PAGE, 1000, 1500, 500f, 500f, fill = 1f)
        assertNear(1000f / 3f, draw.scaledWidth)
        assertNear(500f, draw.scaledHeight)
        assertNear((500f - 1000f / 3f) / 2f, draw.left) // centered horizontally
        assertNear(0f, draw.top)
    }

    @Test
    fun panelRegionFillsContainerAndIsCentered() {
        // Left half of a square page in a square container: the camera region (500x1000 px)
        // is framed so its centre lands at the container centre.
        val camera = Panel(0f, 0f, 0.5f, 1f)
        val draw = computePageDraw(camera, 1000, 1000, 400f, 400f, fill = 1f)
        // Scale fits the 500x1000 region into 400x400 → 0.4; page becomes 400x400... scaled by
        // bitmap: scaledWidth = 1000*0.4 = 400.
        assertNear(400f, draw.scaledWidth)
        assertNear(400f, draw.scaledHeight)
        // Camera centre (250, 500) in bitmap px must map to container centre (200, 200).
        assertNear(200f - 250f * 0.4f, draw.left)
        assertNear(200f - 500f * 0.4f, draw.top)
    }

    @Test
    fun fillFactorLeavesPadding() {
        val padded = computePageDraw(Panel.FULL_PAGE, 1000, 1000, 500f, 500f, fill = 0.9f)
        assertNear(450f, padded.scaledWidth)
        assertNear(450f, padded.scaledHeight)
    }

    @Test
    fun fitToWidthScalesToContainerWidthAndPositionsAtTopWhenTall() {
        // 1000x2000 tall page in 500x500 container, fill = 1f
        val draw = computePageDraw(
            camera = Panel.FULL_PAGE,
            bitmapW = 1000,
            bitmapH = 2000,
            containerW = 500f,
            containerH = 500f,
            fill = 1f,
            fitMode = PageFitMode.FIT_WIDTH,
        )
        // Scaled to width (500px): scale = 500/1000 = 0.5. Height becomes 1000px.
        assertNear(500f, draw.scaledWidth)
        assertNear(1000f, draw.scaledHeight)
        assertNear(0f, draw.left)
        // Since scaledHeight (1000) > containerH (500), top starts at 0 (top of page).
        assertNear(0f, draw.top)
    }

    @Test
    fun fitToWidthCentersVerticallyWhenPageFitsWithinHeight() {
        // 1000x500 wide page in 500x500 container, fill = 1f
        val draw = computePageDraw(
            camera = Panel.FULL_PAGE,
            bitmapW = 1000,
            bitmapH = 500,
            containerW = 500f,
            containerH = 500f,
            fill = 1f,
            fitMode = PageFitMode.FIT_WIDTH,
        )
        // Scaled to width (500px): scale = 0.5. Height becomes 250px.
        assertNear(500f, draw.scaledWidth)
        assertNear(250f, draw.scaledHeight)
        assertNear(0f, draw.left)
        // Since scaledHeight (250) <= containerH (500), centered vertically at (500-250)/2 = 125.
        assertNear(125f, draw.top)
    }

    @Test
    fun degenerateBitmapAndCameraSizesDoNotBlowUp() {
        val draw = computePageDraw(Panel(0.5f, 0.5f, 0.5f, 0.5f), 0, 0, 100f, 100f)
        assertTrue(draw.scaledWidth.isFinite())
        assertTrue(draw.scaledHeight.isFinite())
        assertTrue(draw.left.isFinite())
        assertTrue(draw.top.isFinite())
    }

    @Test
    fun lerpPanelInterpolatesLinearly() {
        val from = Panel(0f, 0f, 1f, 1f)
        val to = Panel(0.2f, 0.4f, 0.6f, 0.8f)
        assertEquals(from, lerpPanel(from, to, 0f))
        assertEquals(to, lerpPanel(from, to, 1f))
        val mid = lerpPanel(from, to, 0.5f)
        assertNear(0.1f, mid.left)
        assertNear(0.2f, mid.top)
        assertNear(0.8f, mid.right)
        assertNear(0.9f, mid.bottom)
    }
}
