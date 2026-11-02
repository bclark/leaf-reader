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
    fun computeSpreadDrawPlacesPagesSideBySideSeamlessly() {
        // Two 1000x1500 pages in a 2000x1000 landscape container, fill = 1f
        val spread = computeSpreadDraw(
            page1W = 1000,
            page1H = 1500,
            page2W = 1000,
            page2H = 1500,
            containerW = 2000f,
            containerH = 1000f,
            fill = 1f,
            fitMode = PageFitMode.FIT_PAGE,
        )
        // Target height = 1500. Total norm width = 2000.
        // Scale = min(2000/2000, 1000/1500) = 2/3.
        // Scaled height = 1000. Scaled W1 = 666.67, Scaled W2 = 666.67.
        // Total W = 1333.33. Centered in 2000 -> boundLeft = (2000 - 1333.33) / 2 = 333.33.
        assertNear(1000f, spread.height1)
        assertNear(1000f, spread.height2)
        assertNear(666.667f, spread.width1)
        assertNear(666.667f, spread.width2)
        assertNear(333.333f, spread.left1)
        assertNear(333.333f + 666.667f, spread.left2) // touching seamlessly
        assertNear(spread.left1 + spread.width1, spread.left2)
        assertNear(0f, spread.top1)
        assertNear(0f, spread.top2)
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

    @Test
    fun computeSpreadTurnMapping_ltrForwardMatchesOpenBookPhysics() {
        // Start spread: pages 4 and 5 (0-based 3 and 4)
        val mapping = computeSpreadTurnMapping(
            pageIndex = 3,
            secondPageIndex = 4,
            pageCount = 10,
            rightToLeft = false,
            isForward = true,
        )
        requireNotNull(mapping)
        assertEquals(3, mapping.staticPageDocIndex)       // Page 4 stays on left
        assertEquals(4, mapping.movingFrontDocIndex)      // Page 5 lifts from right
        assertEquals(5, mapping.movingBackDocIndex)       // Page 6 on reverse face
        assertEquals(6, mapping.underneathDocIndex)       // Page 7 underneath on right
        assertTrue(mapping.isPeelingFromRight)            // Right to left curl
    }

    @Test
    fun computeSpreadTurnMapping_ltrReverseMatchesOpenBookPhysics() {
        // Start spread: pages 6 and 7 (0-based 5 and 6)
        val mapping = computeSpreadTurnMapping(
            pageIndex = 5,
            secondPageIndex = 6,
            pageCount = 10,
            rightToLeft = false,
            isForward = false,
        )
        requireNotNull(mapping)
        assertEquals(6, mapping.staticPageDocIndex)       // Page 7 stays on right
        assertEquals(5, mapping.movingFrontDocIndex)      // Page 6 lifts from left
        assertEquals(4, mapping.movingBackDocIndex)       // Page 5 on reverse face
        assertEquals(3, mapping.underneathDocIndex)       // Page 4 underneath on left
        assertTrue(!mapping.isPeelingFromRight)           // Left to right curl
    }

    @Test
    fun computeSpreadTurnMapping_ltrReverseToCover() {
        // Start spread: pages 2 and 3 (0-based 1 and 2)
        val mapping = computeSpreadTurnMapping(
            pageIndex = 1,
            secondPageIndex = 2,
            pageCount = 10,
            rightToLeft = false,
            isForward = false,
        )
        requireNotNull(mapping)
        assertEquals(2, mapping.staticPageDocIndex)       // Page 3 stays on right
        assertEquals(1, mapping.movingFrontDocIndex)      // Page 2 lifts from left
        assertEquals(0, mapping.movingBackDocIndex)       // Page 1 (cover) on reverse face
        assertEquals(null, mapping.underneathDocIndex)    // Cover is alone, no underneath page
        assertTrue(!mapping.isPeelingFromRight)
    }

    @Test
    fun computeSpreadTurnMapping_rtlForwardMatchesOpenBookPhysics() {
        // In RTL: Page 4 (index 3) is on right, Page 5 (index 4) is on left
        val mapping = computeSpreadTurnMapping(
            pageIndex = 3,
            secondPageIndex = 4,
            pageCount = 10,
            rightToLeft = true,
            isForward = true,
        )
        requireNotNull(mapping)
        assertEquals(3, mapping.staticPageDocIndex)       // Page 4 stays on right
        assertEquals(4, mapping.movingFrontDocIndex)      // Page 5 lifts from left
        assertEquals(5, mapping.movingBackDocIndex)       // Page 6 on reverse face
        assertEquals(6, mapping.underneathDocIndex)       // Page 7 underneath on left
        assertTrue(!mapping.isPeelingFromRight)           // Left to right curl in RTL forward
    }

    @Test
    fun computeSpreadCurlX_flatAndTurnedExtremes() {
        val spineX = 500f
        val w = 400f
        // Peeling from right: s goes from 0 (spine) to w (outer edge).
        // At t=0 (flat on right): sFold = w, r = 0.
        val x0 = computeSpreadCurlX(s = 0f, sFold = w, r = 0f, spineX = spineX, isPeelingFromRight = true)
        val xW = computeSpreadCurlX(s = w, sFold = w, r = 0f, spineX = spineX, isPeelingFromRight = true)
        assertNear(spineX, x0)
        assertNear(spineX + w, xW)

        // At t=1 (fully turned flat on left): sFold = 0, r = 0.
        // For s in 0..w: computeSpreadCurlX should map to spineX - s!
        val xTurned0 = computeSpreadCurlX(s = 0f, sFold = 0f, r = 0f, spineX = spineX, isPeelingFromRight = true)
        val xTurnedW = computeSpreadCurlX(s = w, sFold = 0f, r = 0f, spineX = spineX, isPeelingFromRight = true)
        assertNear(spineX, xTurned0)
        assertNear(spineX - w, xTurnedW)
    }

    @Test
    fun spreadFacesCullHiddenTextureAndSettleReadableInBothDirections() {
        for (right in listOf(false, true)) {
            val w = 400f
            val spine = 500f
            // At rest the back has zero area; at completion the front has zero area.
            for (u in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                assertNear(computeSpreadCurlFaceX(0f, w, w, 0f, spine, right, true),
                    computeSpreadCurlFaceX(u, w, w, 0f, spine, right, true))
                assertNear(spine, computeSpreadCurlFaceX(u, w, 0f, 0f, spine, right, false))
                val expected = if (right) spine - w + u * w else spine + u * w
                assertNear(expected, computeSpreadCurlFaceX(u, w, 0f, 0f, spine, right, true))
            }
            // Both texture faces run left-to-right everywhere they are visible.
            for (step in 0..100) {
                val t = step / 100f
                val r = w * 0.16f * kotlin.math.sin(t * kotlin.math.PI.toFloat()).coerceAtLeast(0f)
                for (back in listOf(false, true)) {
                    var previous = Float.NEGATIVE_INFINITY
                    for (col in 0..100) {
                        val x = computeSpreadCurlFaceX(col / 100f, w, computeSpreadCurlFold(w, t, r), r, spine, right, back)
                        assertTrue(x.isFinite() && x >= previous - 0.001f, "t=$t back=$back right=$right")
                        previous = x
                    }
                }
            }
        }
    }

    @Test
    fun rtlReverseMirrorsPlacementAndBoundariesDoNotInventPages() {
        val mapping = requireNotNull(computeSpreadTurnMapping(5, 6, 10, true, false))
        assertEquals(6, mapping.staticPageDocIndex)
        assertEquals(5, mapping.movingFrontDocIndex)
        assertEquals(4, mapping.movingBackDocIndex)
        assertEquals(3, mapping.underneathDocIndex)
        assertTrue(mapping.isPeelingFromRight)
        assertEquals(null, computeSpreadTurnMapping(0, null, 10, false, false))
        assertEquals(null, computeSpreadTurnMapping(7, 8, 9, false, true))
        val last = requireNotNull(computeSpreadTurnMapping(7, 8, 10, false, true))
        assertEquals(9, last.movingBackDocIndex)
        assertEquals(null, last.underneathDocIndex)
    }

    @Test
    fun sheetEdgeTracksDragAcrossTheSpreadWithoutLag() {
        val width = 400f
        for (step in 0..100) {
            val t = step / 100f
            val r = width * 0.16f * kotlin.math.sin(t * kotlin.math.PI.toFloat()).coerceAtLeast(0f)
            val fold = computeSpreadCurlFold(width, t, r)
            assertNear(width * (1f - 2f * t), computeSpreadCurlX(width, fold, r, 0f, true))
            assertNear(-width * (1f - 2f * t), computeSpreadCurlX(width, fold, r, 0f, false))
        }
    }
}
