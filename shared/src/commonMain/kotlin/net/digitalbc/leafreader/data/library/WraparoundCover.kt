package net.digitalbc.leafreader.data.library

import kotlin.math.abs

/** Recognize a narrow, dark, full-height binding between two portrait-sized faces. */
object WraparoundCover {
    const val columns = 96
    const val rows = 24

    fun isCandidate(width: Int, height: Int): Boolean =
        height >= 32 && width.toDouble() / height in 1.25..1.8

    /** Row-major luminance samples; returns the source x position or -1 for an uncertain image. */
    fun frontStart(width: Int, height: Int, samples: IntArray): Int {
        if (!isCandidate(width, height) || samples.size != columns * rows) return -1
        fun value(column: Int, row: Int) = samples[row * columns + column]
        // A printed title can make the middle of a spine bright, but its outer strips
        // stay flat. Look for those strips separately from the clean two-edge test.
        fun median(values: IntArray) = values.sortedArray()[rows / 2]
        val level = IntArray(columns) { x -> median(IntArray(rows) { y -> value(x, y) }) }
        val spread = IntArray(columns) { x -> median(IntArray(rows) { y -> abs(value(x, y) - level[x]) }) }
        fun letteringScore(start: Int, end: Int): Int {
            val left = level[start]
            val right = level[end - 1]
            if (end - start < 3 || maxOf(left, right) > 65 || abs(left - right) > 28 ||
                maxOf(spread[start], spread[end - 1]) > 10
            ) return 0
            val binding = maxOf(left, right)
            val hasLettering = (start + 1 until end - 1).any { x ->
                (0 until rows).count { y ->
                    value(x, y) >= binding + 60 && abs(value(start, y) - left) <= 24 &&
                        abs(value(end - 1, y) - right) <= 24
                } in 4..17 &&
                    (0 until rows).count { y -> abs(value(x, y) - binding) <= 24 } >= 5
            }
            if (!hasLettering) return 0
            val leftEdge = maxOf(level[start - 1] - left, 2 * (spread[start - 1] - spread[start]))
            val rightEdge = maxOf(level[end] - right, 2 * (spread[end] - spread[end - 1]))
            val weaker = minOf(leftEdge, rightEdge)
            val stronger = maxOf(leftEdge, rightEdge)
            return if (weaker >= 9 && stronger >= 24) 2 * weaker + stronger else 0
        }
        var bestScore = 0
        var bestLetteredScore = 0
        var bestLetteredEnd: Int? = null
        var bestEnd: Int? = null
        for (start in 40..49) {
            for (end in start + 2..minOf(start + 9, 56)) {
                if (start * width.toDouble() / columns / height !in 0.54..0.83 ||
                    (columns - end) * width.toDouble() / columns / height !in 0.54..0.83
                ) continue
                var score = 0
                var rowsWithBinding = 0
                for (row in 0 until rows) {
                    val center = value((start + end - 1) / 2, row)
                    val insideLeft = value(start, row)
                    val insideRight = value(end - 1, row)
                    val leftStep = value(start - 1, row) - insideLeft
                    val rightStep = value(end, row) - insideRight
                    if (center <= 72 && abs(insideLeft - center) <= 22 && abs(insideRight - center) <= 22 &&
                        leftStep >= 12 && rightStep >= 12
                    ) {
                        rowsWithBinding++
                        score += minOf(leftStep, rightStep)
                    }
                }
                val letteredScore = letteringScore(start, end)
                if (letteredScore > bestLetteredScore) {
                    bestLetteredScore = letteredScore
                    bestLetteredEnd = end
                }
                if (rowsWithBinding >= 18 && score > bestScore) {
                    bestScore = score
                    bestEnd = end
                }
            }
        }
        // Prefer the full image when we cannot identify a binding. The right face is the front
        // of the Western wrap convention; this heuristic isn't a reading-direction detector.
        return (bestEnd ?: bestLetteredEnd)?.let { it * width / columns } ?: -1
    }
}
