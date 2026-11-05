package net.digitalbc.leafreader.ui.reader

import kotlin.math.abs

/** Portable intent/threshold policy; native gesture owners handle zoom, scrolling and cancellation. */
object ReaderDismissGesture {
    // Tune this one value to change how far down the sheet must travel before going back.
    const val DISMISS_FRACTION = 0.66f

    fun isDownward(deltaX: Float, deltaY: Float): Boolean =
        deltaY > 0f && deltaY > abs(deltaX)

    fun progress(deltaY: Float, viewportHeight: Float): Float =
        if (viewportHeight > 0f) (deltaY / viewportHeight).coerceIn(0f, 1f) else 0f

    // Project release momentum this many seconds ahead, in the same pixel units as the drag.
    const val VELOCITY_PROJECTION_SECONDS = 0.30f
    const val ENGAGE_DISTANCE_DP = 48f
    const val DRAG_RESISTANCE = 0.55f

    /** A quick downward flick can cross the threshold without dragging the sheet all the way. */
    fun shouldDismiss(deltaY: Float, viewportHeight: Float, velocityY: Float = 0f): Boolean =
        viewportHeight > 0f &&
            deltaY + velocityY * VELOCITY_PROJECTION_SECONDS >= viewportHeight * DISMISS_FRACTION
}
