package net.digitalbc.leafreader.detection

/**
 * Refines ML-detected panel boxes using image pixel data:
 *
 * 1. **Gutter Splitting**: If the detector merged two or more distinct panels into a single box,
 *    an interior blank channel (gutter) will run through the box. We detect clear interior gutters
 *    (Otsu-thresholded blank lines) and split the merged panel into its real sub-panels, carving
 *    out the empty gutter between them.
 *
 * 2. **Edge Snapping**: Detected bounding boxes often have slight alignment errors due to
 *    letterbox downsampling. We search in a small window (±2.5% of page size) around each edge
 *    to snap it to the nearest gutter line, ensuring clean boundaries without clipped artwork.
 *
 * Platform independent: operates on a downsampled grayscale buffer (`ByteArray`), so both Android
 * and iOS achieve exact parity.
 */
class GutterRefiner(
    private val gray: ByteArray,
    private val gw: Int,
    private val gh: Int,
    private val confidence: Double = 0.88,
    private val edgeMargin: Double = 0.05,
    private val minPanelFrac: Float = 0.05f,
    private val maxDepth: Int = 4,
    private val snapWindowFrac: Float = 0.03f,
) {
    /** True if the page has dark/black gutters (e.g. night scenes, noir comics) instead of white. */
    val darkGutters: Boolean

    /** Otsu threshold for separating paper/gutter from ink/content. */
    val blankLevel: Int

    init {
        require(gw > 0 && gh > 0 && gray.size >= gw * gh) { "Invalid grayscale buffer dimensions" }
        darkGutters = detectDarkGutters()
        blankLevel = otsuThreshold()
    }

    /**
     * Refines the list of detected panels by snapping boundaries to nearby gutters and
     * recursively splitting any box that contains an interior gutter channel.
     */
    fun refine(panels: List<Panel>): List<Panel> {
        if (panels.isEmpty()) return panels
        return panels.flatMap { panel ->
            val snapped = snapEdges(panel)
            split(snapped, depth = maxDepth)
        }
    }

    /**
     * Snaps a panel's left, top, right, and bottom edges to the nearest gutter lines if one is
     * within [snapWindowFrac] of the page dimension.
     */
    fun snapEdges(p: Panel): Panel {
        val snapX = (gw * snapWindowFrac).toInt().coerceAtLeast(2)
        val snapY = (gh * snapWindowFrac).toInt().coerceAtLeast(2)

        val x0 = (p.left * gw).toInt().coerceIn(0, gw - 1)
        val x1 = (p.right * gw).toInt().coerceIn(x0 + 1, gw)
        val y0 = (p.top * gh).toInt().coerceIn(0, gh - 1)
        val y1 = (p.bottom * gh).toInt().coerceIn(y0 + 1, gh)

        val snappedLeft = findGutterSnap(vertical = true, target = x0, searchRadius = snapX, start = y0, end = y1, snapToStart = false)
        val snappedRight = findGutterSnap(vertical = true, target = x1, searchRadius = snapX, start = y0, end = y1, snapToStart = true)
        val snappedTop = findGutterSnap(vertical = false, target = y0, searchRadius = snapY, start = x0, end = x1, snapToStart = false)
        val snappedBottom = findGutterSnap(vertical = false, target = y1, searchRadius = snapY, start = x0, end = x1, snapToStart = true)

        val newLeft = (snappedLeft?.toFloat() ?: x0.toFloat()) / gw
        val newRight = (snappedRight?.toFloat() ?: x1.toFloat()) / gw
        val newTop = (snappedTop?.toFloat() ?: y0.toFloat()) / gh
        val newBottom = (snappedBottom?.toFloat() ?: y1.toFloat()) / gh

        val validW = newRight - newLeft >= minPanelFrac
        val validH = newBottom - newTop >= minPanelFrac

        return Panel(
            left = if (validW) newLeft else p.left,
            top = if (validH) newTop else p.top,
            right = if (validW) newRight else p.right,
            bottom = if (validH) newBottom else p.bottom,
        )
    }

    /**
     * Recursively splits a panel along any interior gutter channel, carving out the gutter band.
     */
    fun split(p: Panel, depth: Int = maxDepth): List<Panel> {
        if (depth <= 0) return listOf(p)

        val x0 = (p.left * gw).toInt().coerceIn(0, gw - 1)
        val x1 = (p.right * gw).toInt().coerceIn(x0 + 1, gw)
        val y0 = (p.top * gh).toInt().coerceIn(0, gh - 1)
        val y1 = (p.bottom * gh).toInt().coerceIn(y0 + 1, gh)

        if ((x1 - x0) < 12 || (y1 - y0) < 12) return listOf(p)

        val vCut = bestCut(vertical = true, x0 = x0, x1 = x1, y0 = y0, y1 = y1)
        val hCut = bestCut(vertical = false, x0 = x0, x1 = x1, y0 = y0, y1 = y1)

        // Adaptive: take whichever axis has the cleaner gutter (highest confidence)
        if (vCut != null && (hCut == null || vCut.confidence >= hCut.confidence)) {
            val cutStartN = vCut.gutterStart.toFloat() / gw
            val cutEndN = vCut.gutterEnd.toFloat() / gw
            if (cutStartN - p.left >= minPanelFrac && p.right - cutEndN >= minPanelFrac) {
                val left = Panel(p.left, p.top, cutStartN, p.bottom)
                val right = Panel(cutEndN, p.top, p.right, p.bottom)
                return split(left, depth - 1) + split(right, depth - 1)
            }
        }
        if (hCut != null) {
            val cutStartN = hCut.gutterStart.toFloat() / gh
            val cutEndN = hCut.gutterEnd.toFloat() / gh
            if (cutStartN - p.top >= minPanelFrac && p.bottom - cutEndN >= minPanelFrac) {
                val top = Panel(p.left, p.top, p.right, cutStartN)
                val bot = Panel(p.left, cutEndN, p.right, p.bottom)
                return split(top, depth - 1) + split(bot, depth - 1)
            }
        }
        return listOf(p)
    }

    data class CutResult(
        val gutterStart: Int,
        val gutterEnd: Int,
        val confidence: Double,
    )

    /**
     * Finds the highest-confidence blank gutter band within the panel's central band.
     */
    private fun bestCut(vertical: Boolean, x0: Int, x1: Int, y0: Int, y1: Int): CutResult? {
        val lineCount = if (vertical) (x1 - x0) else (y1 - y0)
        val span = if (vertical) (y1 - y0) else (x1 - x0)
        if (lineCount < 12 || span < 8) return null

        val lo = (lineCount * edgeMargin).toInt()
        val hi = lineCount - lo
        if (hi - lo < 1) return null

        val conf = DoubleArray(lineCount)
        var bestIdx = -1
        var bestConf = 0.0

        for (i in lo until hi) {
            val lineIdx = if (vertical) x0 + i else y0 + i
            val start = if (vertical) y0 else x0
            val end = if (vertical) y1 else x1
            val c = blankFraction(vertical, lineIdx, start, end)
            conf[i] = c
            if (c > bestConf) {
                bestConf = c
                bestIdx = i
            }
        }

        if (bestIdx < 0 || bestConf < confidence) return null

        // Expand to the full contiguous band of blank gutter lines around the peak
        var a = bestIdx
        var b = bestIdx
        while (a > lo && conf[a - 1] >= confidence) a--
        while (b < hi - 1 && conf[b + 1] >= confidence) b++

        val offset = if (vertical) x0 else y0
        return CutResult(
            gutterStart = offset + a,
            gutterEnd = offset + b + 1,
            confidence = bestConf,
        )
    }

    /**
     * Looks within [target - searchRadius, target + searchRadius] for a prominent gutter band.
     */
    private fun findGutterSnap(
        vertical: Boolean,
        target: Int,
        searchRadius: Int,
        start: Int,
        end: Int,
        snapToStart: Boolean,
    ): Int? {
        val maxCoord = if (vertical) gw - 1 else gh - 1
        val lo = (target - searchRadius).coerceIn(0, maxCoord)
        val hi = (target + searchRadius).coerceIn(0, maxCoord)

        var bestStart = -1
        var bestEnd = -1
        var maxLen = 0

        var cur = lo
        while (cur <= hi) {
            if (blankFraction(vertical, cur, start, end) >= 0.82) {
                val bandStart = cur
                while (cur <= hi && blankFraction(vertical, cur, start, end) >= 0.82) {
                    cur++
                }
                val bandLen = cur - bandStart
                if (bandLen > maxLen) {
                    maxLen = bandLen
                    bestStart = bandStart
                    bestEnd = cur
                }
            } else {
                cur++
            }
        }

        if (maxLen <= 0) return null
        return if (snapToStart) bestStart else bestEnd
    }

    /** Fraction of line that is blank (conforms to gutter/paper level). */
    private fun blankFraction(vertical: Boolean, index: Int, start: Int, end: Int): Double {
        if (start >= end) return 0.0
        var blank = 0
        val total = end - start
        if (vertical) {
            for (y in start until end) {
                val v = gray[y * gw + index].toInt() and 0xFF
                if (isBlank(v)) blank++
            }
        } else {
            val rowOffset = index * gw
            for (x in start until end) {
                val v = gray[rowOffset + x].toInt() and 0xFF
                if (isBlank(v)) blank++
            }
        }
        return blank.toDouble() / total
    }

    private fun isBlank(pixel: Int): Boolean =
        if (darkGutters) pixel <= blankLevel else pixel >= blankLevel

    /** Determines if the page background/gutter is predominantly dark by sampling page margins. */
    private fun detectDarkGutters(): Boolean {
        var sum = 0L
        var count = 0
        val marginX = (gw * 0.03).toInt().coerceAtLeast(1)
        val marginY = (gh * 0.03).toInt().coerceAtLeast(1)

        // Top & bottom margin rows
        for (y in 0 until marginY) {
            for (x in 0 until gw) {
                sum += gray[y * gw + x].toInt() and 0xFF
                sum += gray[(gh - 1 - y) * gw + x].toInt() and 0xFF
                count += 2
            }
        }
        // Left & right margin cols
        for (y in marginY until (gh - marginY)) {
            for (x in 0 until marginX) {
                sum += gray[y * gw + x].toInt() and 0xFF
                sum += gray[y * gw + (gw - 1 - x)].toInt() and 0xFF
                count += 2
            }
        }
        val avg = if (count > 0) sum / count else 255L
        return avg < 80 // Dark margin indicates black gutters
    }

    /** Otsu's thresholding to separate ink from paper. */
    private fun otsuThreshold(): Int {
        val hist = IntArray(256)
        for (b in gray) {
            val v = b.toInt() and 0xFF
            hist[v]++
        }
        val total = gray.size
        if (total == 0) return if (darkGutters) 50 else 200

        var sumAll = 0.0
        for (t in 0 until 256) sumAll += t * hist[t]

        var sumB = 0.0
        var wB = 0
        var best = -1.0
        var bestStart = if (darkGutters) 50 else 200
        var bestEnd = bestStart

        for (t in 0 until 256) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t * hist[t]
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best + 1e-4) {
                best = between
                bestStart = t
                bestEnd = t
            } else if (kotlin.math.abs(between - best) <= 1e-4) {
                bestEnd = t
            }
        }
        return (bestStart + bestEnd) / 2
    }
}
