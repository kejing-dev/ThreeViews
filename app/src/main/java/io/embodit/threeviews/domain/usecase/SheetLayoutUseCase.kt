package io.embodit.threeviews.domain.usecase

import io.embodit.threeviews.domain.geometry.PartDrawing
import io.embodit.threeviews.domain.geometry.ViewDrawing
import io.embodit.threeviews.domain.geometry.ViewKind
import io.embodit.threeviews.domain.model.AlignmentGuide
import io.embodit.threeviews.domain.model.AlignmentRule
import io.embodit.threeviews.domain.model.SheetLayout
import io.embodit.threeviews.domain.model.SheetPoint
import io.embodit.threeviews.domain.model.ViewPlacement
import kotlin.math.abs

/**
 * Arranges the three views the way a first-angle drawing does: the front view top-left, the top
 * view directly below it and the left view directly to its right. The gap equals twice the plane
 * margin of the projection box, so the unfolded box and the sheet are the same picture.
 */
class SheetLayoutUseCase {

    fun layout(drawing: PartDrawing, gap: Double = DEFAULT_GAP): SheetLayout {
        require(gap > 0.0) { "gap must be positive" }
        val fb = drawing.front.bounds
        val tb = drawing.top.bounds
        val lb = drawing.left.bounds
        val front = ViewPlacement(ViewKind.FRONT, 0.0, 0.0, fb.width, fb.height, fb.minU, fb.maxV)
        val top = ViewPlacement(ViewKind.TOP, 0.0, fb.height + gap, tb.width, tb.height, tb.minU, tb.maxV)
        val left = ViewPlacement(ViewKind.LEFT, fb.width + gap, 0.0, lb.width, lb.height, lb.minU, lb.maxV)
        val depth = tb.height

        // The 45 degree line runs from the origin of the axes through the corner where the top
        // view's back edge meets the left view's back edge.
        val origin = SheetPoint(front.right + gap / 2, front.bottom + gap / 2)
        val miterEnd = SheetPoint(left.x + depth + MITER_OVERHANG, top.y + depth + MITER_OVERHANG)

        // 长对正: an x shared by the front and top views. Offsets are measured from the left edge.
        val lengths = shared(
            extent = fb.width,
            verticalLines(drawing.front).map { it - fb.minU } + verticalLines(drawing.top).map { it - tb.minU },
        )
        // 高平齐: a y shared by the front and left views. Offsets are measured from the top edge.
        val heights = shared(
            extent = fb.height,
            horizontalLines(drawing.front).map { fb.maxV - it } + horizontalLines(drawing.left).map { lb.maxV - it },
        )
        // 宽相等: a depth shared by the top and left views. Offsets are measured from the back of the part.
        val widths = shared(
            extent = depth,
            horizontalLines(drawing.top).map { tb.maxV - it } + verticalLines(drawing.left).map { it - lb.minU },
        )

        val guides = buildList {
            for (x in lengths) add(AlignmentGuide(AlignmentRule.LENGTH, listOf(SheetPoint(x, front.bottom), SheetPoint(x, top.y))))
            for (y in heights) add(AlignmentGuide(AlignmentRule.HEIGHT, listOf(SheetPoint(front.right, y), SheetPoint(left.x, y))))
            // A width in the top view travels right to the 45 degree line, then up into the left view.
            for (d in widths) {
                add(
                    AlignmentGuide(
                        AlignmentRule.WIDTH,
                        listOf(SheetPoint(top.right, top.y + d), SheetPoint(left.x + d, top.y + d), SheetPoint(left.x + d, left.bottom)),
                    ),
                )
            }
        }
        val holds = mapOf(
            AlignmentRule.LENGTH to (same(fb.width, tb.width) && same(fb.minU, tb.minU)),
            AlignmentRule.HEIGHT to (same(fb.height, lb.height) && same(fb.maxV, lb.maxV)),
            AlignmentRule.WIDTH to same(tb.height, lb.width),
        )
        return SheetLayout(
            front = front,
            top = top,
            left = left,
            gap = gap,
            width = left.right,
            height = top.bottom,
            miterStart = origin,
            miterEnd = miterEnd,
            guides = guides,
            origin = origin,
            holds = holds,
        )
    }

    /** The u of every straight vertical line of a view, visible or hidden. Faceted circles do not count. */
    private fun verticalLines(view: ViewDrawing): List<Double> =
        (view.visible + view.hidden).flatMap { it.segments() }
            .filter { (a, b) -> abs(a.u - b.u) < STRAIGHT && abs(a.v - b.v) >= MIN_FEATURE }
            .map { it.first.u }

    /** The v of every straight horizontal line of a view, visible or hidden. */
    private fun horizontalLines(view: ViewDrawing): List<Double> =
        (view.visible + view.hidden).flatMap { it.segments() }
            .filter { (a, b) -> abs(a.v - b.v) < STRAIGHT && abs(a.u - b.u) >= MIN_FEATURE }
            .map { it.first.v }

    /**
     * The offsets worth a guide: the two ends of the extent always, then the features in between,
     * from the outside in, up to [MAX_GUIDES] in total.
     */
    private fun shared(extent: Double, features: List<Double>): List<Double> {
        val inner = features.filter { it > SAME && it < extent - SAME }.sorted()
            .fold(ArrayList<Double>()) { kept, value -> if (kept.isEmpty() || value - kept.last() > SAME) kept.add(value); kept }
        return (listOf(0.0, extent) + inner.take(MAX_GUIDES - 2)).sorted()
    }

    private fun same(a: Double, b: Double) = abs(a - b) < SAME

    companion object {
        /** Twice [BoxLayoutUseCase.DEFAULT_MARGIN]. */
        const val DEFAULT_GAP = 28.0
        const val MITER_OVERHANG = 6.0

        /** More guides than this per rule would bury the drawing. */
        const val MAX_GUIDES = 6
        private const val STRAIGHT = 1e-6
        private const val SAME = 1e-3

        /** Facets of a 48-sided circle are shorter than this; real edges are longer. */
        private const val MIN_FEATURE = 2.0
    }
}
