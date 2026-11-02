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

/**
 * Layout geometry for two pages displayed side-by-side in a two-page spread.
 */
data class SpreadDraw(
    val left1: Float,
    val top1: Float,
    val width1: Float,
    val height1: Float,
    val left2: Float,
    val top2: Float,
    val width2: Float,
    val height2: Float,
    val totalWidth: Float,
    val totalHeight: Float,
    val boundLeft: Float,
    val boundTop: Float,
)

/**
 * Computes layout for two pages displayed side-by-side in a spread, normalized to matching height.
 */
fun computeSpreadDraw(
    page1W: Int,
    page1H: Int,
    page2W: Int,
    page2H: Int,
    containerW: Float,
    containerH: Float,
    fill: Float = 0.98f,
    fitMode: PageFitMode = PageFitMode.FIT_PAGE,
): SpreadDraw {
    val p1w = page1W.toFloat().coerceAtLeast(1f)
    val p1h = page1H.toFloat().coerceAtLeast(1f)
    val p2w = page2W.toFloat().coerceAtLeast(1f)
    val p2h = page2H.toFloat().coerceAtLeast(1f)

    val targetH = maxOf(p1h, p2h)
    val normW1 = p1w * (targetH / p1h)
    val normW2 = p2w * (targetH / p2h)
    val totalNormW = normW1 + normW2

    val scale = when (fitMode) {
        PageFitMode.FIT_PAGE -> minOf(containerW / totalNormW, containerH / targetH) * fill
        PageFitMode.FIT_WIDTH -> (containerW / totalNormW) * fill
    }

    val scaledW1 = normW1 * scale
    val scaledW2 = normW2 * scale
    val scaledH = targetH * scale
    val totalW = scaledW1 + scaledW2

    val boundLeft = (containerW - totalW) / 2f
    val boundTop = if (fitMode == PageFitMode.FIT_WIDTH && scaledH > containerH) {
        (containerH - containerH * fill) / 2f
    } else {
        (containerH - scaledH) / 2f
    }

    return SpreadDraw(
        left1 = boundLeft,
        top1 = boundTop,
        width1 = scaledW1,
        height1 = scaledH,
        left2 = boundLeft + scaledW1,
        top2 = boundTop,
        width2 = scaledW2,
        height2 = scaledH,
        totalWidth = totalW,
        totalHeight = scaledH,
        boundLeft = boundLeft,
        boundTop = boundTop,
    )
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

/**
 * Resolved document page positions for a physical two-page spread turn.
 *
 * @param staticPageDocIndex The document page on the stationary side (stays visible until covered).
 * @param movingFrontDocIndex The document page on the front of the turning sheet (lifts and curls).
 * @param movingBackDocIndex The document page on the reverse face of the turning sheet (visible as sheet turns over, upright).
 * @param underneathDocIndex The document page revealed on the active side as the moving sheet lifts away (null if at end).
 * @param isPeelingFromRight True if turning sheet from right to left; false if turning sheet from left to right.
 */
data class SpreadTurnMapping(
    val staticPageDocIndex: Int,
    val movingFrontDocIndex: Int,
    val movingBackDocIndex: Int,
    val underneathDocIndex: Int?,
    val isPeelingFromRight: Boolean,
)

/**
 * Resolves document page mapping for an open-book two-page spread turn in LTR and RTL reading directions.
 */
fun computeSpreadTurnMapping(
    pageIndex: Int,
    secondPageIndex: Int?,
    pageCount: Int,
    rightToLeft: Boolean,
    isForward: Boolean,
): SpreadTurnMapping? {
    if (secondPageIndex == null) return null
    val rtl = rightToLeft
    val currentLeft = if (rtl) secondPageIndex else pageIndex
    val currentRight = if (rtl) pageIndex else secondPageIndex

    return if (isForward) {
        val target = pageIndex + 2
        if (target >= pageCount) return null
        val nextP1 = target
        val nextP2 = if (target + 1 < pageCount) target + 1 else null
        val nextLeft = if (!rtl) nextP1 else nextP2
        val nextRight = if (!rtl) nextP2 else nextP1
        val movingBack = if (!rtl) (nextLeft ?: nextP1) else (nextRight ?: nextP1)
        val underneath = if (!rtl) nextRight else nextLeft

        SpreadTurnMapping(
            staticPageDocIndex = if (!rtl) currentLeft else currentRight,
            movingFrontDocIndex = if (!rtl) currentRight else currentLeft,
            movingBackDocIndex = movingBack,
            underneathDocIndex = underneath,
            isPeelingFromRight = !rtl,
        )
    } else {
        if (pageIndex <= 0) return null
        val target = if (pageIndex % 2 == 1) (pageIndex - 2).coerceAtLeast(0) else pageIndex - 1
        val prevP1 = target
        val prevP2 = if (target > 0 && target + 1 < pageIndex) target + 1 else null
        val prevLeft = if (!rtl) (if (prevP2 != null) prevP1 else null) else prevP2
        val prevRight = if (!rtl) (prevP2 ?: prevP1) else prevP1
        val movingBack = if (!rtl) prevRight else (prevLeft ?: prevRight)
        val underneath = if (!rtl) prevLeft else (if (prevP2 != null) prevRight else null)

        SpreadTurnMapping(
            staticPageDocIndex = if (!rtl) currentRight else currentLeft,
            movingFrontDocIndex = if (!rtl) currentLeft else currentRight,
            movingBackDocIndex = movingBack,
            underneathDocIndex = underneath,
            isPeelingFromRight = rtl,
        )
    }
}

/**
 * Computes the deformed x-coordinate for a point on a sheet at arc-distance [s] from the center spine.
 *
 * @param s Arc distance along the sheet from the spine (0..W).
 * @param sFold Distance along the sheet where the fold starts (W*(1-t)).
 * @param r Radius of the cylindrical roll.
 * @param spineX X position of the center spine on screen.
 * @param isPeelingFromRight True if peeling right sheet toward left; false if peeling left sheet toward right.
 */
fun computeSpreadCurlX(
    s: Float,
    sFold: Float,
    r: Float,
    spineX: Float,
    isPeelingFromRight: Boolean,
): Float {
    return if (isPeelingFromRight) {
        if (s <= sFold) {
            spineX + s
        } else {
            val deltaS = s - sFold
            if (r > 0.001f) {
                val theta = deltaS / r
                if (theta <= kotlin.math.PI.toFloat()) {
                    (spineX + sFold) + r * kotlin.math.sin(theta)
                } else {
                    (spineX + sFold) - (deltaS - kotlin.math.PI.toFloat() * r)
                }
            } else {
                (spineX + sFold) - deltaS
            }
        }
    } else {
        if (s <= sFold) {
            spineX - s
        } else {
            val deltaS = s - sFold
            if (r > 0.001f) {
                val theta = deltaS / r
                if (theta <= kotlin.math.PI.toFloat()) {
                    (spineX - sFold) - r * kotlin.math.sin(theta)
                } else {
                    (spineX - sFold) + (deltaS - kotlin.math.PI.toFloat() * r)
                }
            } else {
                (spineX - sFold) + deltaS
            }
        }
    }
}

/** Texture-coordinate projection with the hidden face collapsed at the roll's apex. */
fun computeSpreadCurlFaceX(
    u: Float,
    width: Float,
    fold: Float,
    radius: Float,
    spine: Float,
    fromRight: Boolean,
    back: Boolean,
): Float {
    val boundary = (fold + radius * kotlin.math.PI.toFloat() / 2f).coerceIn(0f, width)
    val distance = (if (fromRight != back) u else 1f - u) * width
    val visible = if (back) maxOf(distance, boundary) else minOf(distance, boundary)
    return computeSpreadCurlX(visible, fold, radius, spine, fromRight)
}

/** Account for the rolled arc so the outer edge follows a drag across both pages. */
fun computeSpreadCurlFold(width: Float, progress: Float, radius: Float): Float =
    (width * (1f - progress.coerceIn(0f, 1f)) - kotlin.math.PI.toFloat() * radius / 2f).coerceAtLeast(0f)
