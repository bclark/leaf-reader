package net.digitalbc.leafreader.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderDismissGestureTest {
    @Test fun commitsAtTheConfiguredFractionAcrossViewportSizes() {
        for (height in listOf(400f, 800f, 1200f)) {
            val threshold = height * ReaderDismissGesture.DISMISS_FRACTION
            assertFalse(ReaderDismissGesture.shouldDismiss(threshold - 1f, height))
            assertTrue(ReaderDismissGesture.shouldDismiss(threshold, height))
            assertTrue(ReaderDismissGesture.shouldDismiss(threshold + 1f, height))
        }
        assertFalse(ReaderDismissGesture.shouldDismiss(300f, 0f))
    }

    @Test fun releaseMomentumCanFinishAShortFlickButHoldingOrReversingDoesNot() {
        assertTrue(ReaderDismissGesture.shouldDismiss(100f, 800f, 1600f))
        assertFalse(ReaderDismissGesture.shouldDismiss(100f, 800f, 200f))
        assertFalse(ReaderDismissGesture.shouldDismiss(100f, 800f, 0f))
        assertFalse(ReaderDismissGesture.shouldDismiss(540f, 800f, -600f))
        assertFalse(ReaderDismissGesture.shouldDismiss(100f, 0f, 5000f))
    }

    @Test fun diagonalDownwardIntentArmsButHorizontalAndUpwardIntentDoNot() {
        assertTrue(ReaderDismissGesture.isDownward(150f, 200f))
        assertTrue(ReaderDismissGesture.isDownward(-150f, 200f))
        assertFalse(ReaderDismissGesture.isDownward(200f, 200f))
        assertFalse(ReaderDismissGesture.isDownward(300f, 0f))
        assertFalse(ReaderDismissGesture.isDownward(0f, -300f))
    }

    @Test fun progressClampsForVisualEffectsAndPullingBackCancelsBeforeCommit() {
        assertEquals(0.5f, ReaderDismissGesture.progress(400f, 800f))
        assertEquals(0f, ReaderDismissGesture.progress(-50f, 800f))
        assertEquals(1f, ReaderDismissGesture.progress(1000f, 800f))
        assertEquals(0f, ReaderDismissGesture.progress(100f, 0f))
        assertFalse(ReaderDismissGesture.shouldDismiss(60f, 800f))
    }
}
