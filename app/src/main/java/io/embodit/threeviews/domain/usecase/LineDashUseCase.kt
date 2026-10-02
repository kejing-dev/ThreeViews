package io.embodit.threeviews.domain.usecase

import io.embodit.threeviews.domain.geometry.Polyline2
import io.embodit.threeviews.domain.geometry.Vec2

/** One straight stroke in drawing coordinates. */
data class Stroke2(val a: Vec2, val b: Vec2) {
    val length: Double get() = a.distanceTo(b)
}

/**
 * Cuts polylines into the strokes of a GB/T 4457.4 line type. The pattern keeps running around
 * corners and along faceted circles, so dashes do not restart at every vertex.
 */
class LineDashUseCase {

    /** Continuous line: one stroke per segment. */
    fun solid(line: Polyline2): List<Stroke2> = segments(line).map { Stroke2(it.first, it.second) }

    /**
     * Dashed or chain line. [pattern] alternates drawn and skipped lengths in millimetres and
     * starts with a drawn length, for example [4, 1] or [9, 1.2, 1, 1.2].
     */
    fun dashed(line: Polyline2, pattern: DoubleArray): List<Stroke2> {
        require(pattern.isNotEmpty() && pattern.size % 2 == 0) { "pattern needs drawn/skipped pairs" }
        require(pattern.all { it > 0.0 }) { "pattern lengths must be positive" }
        val strokes = ArrayList<Stroke2>()
        var index = 0
        var left = pattern[0]
        for ((a, b) in segments(line)) {
            val total = a.distanceTo(b)
            if (total < 1e-9) continue
            var at = 0.0
            while (at < total - 1e-9) {
                val step = minOf(left, total - at)
                if (index % 2 == 0) strokes.add(Stroke2(lerp(a, b, at / total), lerp(a, b, (at + step) / total)))
                at += step
                left -= step
                if (left <= 1e-9) {
                    index = (index + 1) % pattern.size
                    left = pattern[index]
                }
            }
        }
        return strokes
    }

    private fun segments(line: Polyline2): List<Pair<Vec2, Vec2>> {
        val pts = line.points
        if (pts.size < 2) return emptyList()
        val result = ArrayList<Pair<Vec2, Vec2>>(pts.size)
        for (i in 0 until pts.size - 1) result.add(pts[i] to pts[i + 1])
        if (line.closed) result.add(pts.last() to pts.first())
        return result
    }

    private fun lerp(a: Vec2, b: Vec2, t: Double) = Vec2(a.u + (b.u - a.u) * t, a.v + (b.v - a.v) * t)

    companion object {
        /** Hidden outline: dash 4 mm, gap 1 mm. */
        val HIDDEN = doubleArrayOf(4.0, 1.0)

        /** Centre line: long dash 9 mm, gap 1.2 mm, short dash 1 mm, gap 1.2 mm. */
        val CENTER = doubleArrayOf(9.0, 1.2, 1.0, 1.2)

        /** Line widths in millimetres: thick for visible outlines, thin for everything else. */
        const val THICK = 0.7
        const val THIN = 0.35
    }
}
