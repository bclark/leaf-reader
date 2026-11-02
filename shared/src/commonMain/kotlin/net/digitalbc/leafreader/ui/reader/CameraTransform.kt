package net.digitalbc.leafreader.ui.reader

import net.digitalbc.leafreader.detection.Panel

/**
 * Where to draw the full page bitmap (top-left + scaled size, in container pixels) so that a given
 * [Panel] region fills the container with "contain" fit (whole panel visible, aspect preserved,
 * centered). The same math handles the full-page view (panel = whole page) and any sub-panel.
 */
data class PageDraw(
    val left: Float,
    val top: Float,
    val scaledWidth: Float,
    val scaledHeight: Float,
)

/** Mode for framing pages in whole-page viewing. */
enum class PageFitMode {
    /** Contain the whole page within the container (default). */
    FIT_PAGE,
    /** Scale page width to container width (allowing vertical scrolling for tall pages). */
    FIT_WIDTH,
}

/**
 * Computes the [PageDraw] that frames [camera] (a normalized page rect) inside a container of size
 * [containerW] x [containerH], given the page bitmap's pixel size. [fill] < 1 leaves padding around
 * the framed region so panels don't touch the screen edges.
 */
fun computePageDraw(
    camera: Panel,
    bitmapW: Int,
    bitmapH: Int,
    containerW: Float,
    containerH: Float,
    fill: Float = 0.98f,
    fitMode: PageFitMode = PageFitMode.FIT_PAGE,
): PageDraw {
    val bw = bitmapW.toFloat().coerceAtLeast(1f)
    val bh = bitmapH.toFloat().coerceAtLeast(1f)

    // Camera region in bitmap pixels.
    val camW = (camera.width * bw).coerceAtLeast(1f)
    val camH = (camera.height * bh).coerceAtLeast(1f)
    val camCx = camera.centerX * bw
    val camCy = camera.centerY * bh

    // Scale so the camera region fits the container according to the chosen fit mode.
    val scale = when (fitMode) {
        PageFitMode.FIT_PAGE -> minOf(containerW / camW, containerH / camH) * fill
        PageFitMode.FIT_WIDTH -> (containerW / camW) * fill
    }

    val scaledWidth = bw * scale
    val scaledHeight = bh * scale
    val left = containerW / 2f - camCx * scale
    val top = if (fitMode == PageFitMode.FIT_WIDTH && scaledHeight > containerH) {
        // In fit-to-width mode when the page is taller than the screen, start at the top margin
        val topPadding = (containerH - containerH * fill) / 2f
        topPadding - camera.top * scaledHeight
    } else {
        containerH / 2f - camCy * scale
    }

    return PageDraw(left, top, scaledWidth, scaledHeight)
}

/** Linear interpolation between two panels, for animating the camera between views. */
fun lerpPanel(from: Panel, to: Panel, t: Float): Panel {
    fun l(a: Float, b: Float) = a + (b - a) * t
    return Panel(
        left = l(from.left, to.left),
        top = l(from.top, to.top),
        right = l(from.right, to.right),
        bottom = l(from.bottom, to.bottom),
    )
}
