package io.embodit.threeviews.domain.usecase

import io.embodit.threeviews.domain.geometry.Polyline2
import io.embodit.threeviews.domain.geometry.Segment3
import io.embodit.threeviews.domain.geometry.Vec2
import io.embodit.threeviews.domain.geometry.Vec3
import io.embodit.threeviews.domain.geometry.ViewKind
import io.embodit.threeviews.domain.model.BoxLayout
import io.embodit.threeviews.domain.model.BoxSceneSpec
import io.embodit.threeviews.domain.model.DrawingStatus
import io.embodit.threeviews.domain.model.PlaneLines
import io.embodit.threeviews.domain.model.RibbonMesh
import kotlin.math.abs

/**
 * Turns a computed drawing into the geometry of the projection box: the three views drawn on their
 * planes with the proper line types, the plane outlines, the projection rays and the inked edges of
 * the part. Everything is in part space (millimetres) for the folded box.
 */
class BoxSceneUseCase(
    private val dash: LineDashUseCase = LineDashUseCase(),
    private val boxLayout: BoxLayoutUseCase = BoxLayoutUseCase(),
) {

    fun build(ready: DrawingStatus.Ready): BoxSceneSpec {
        val drawing = ready.drawing
        val box = ready.box
        val planes = ViewKind.entries.map { view ->
            val lines = drawing.view(view)
            val normal = view.towardViewer
            fun lift(strokes: List<Stroke2>, lift: Double) =
                strokes.map { Segment3(onPlane(it.a, view, box, lift), onPlane(it.b, view, box, lift)) }
            PlaneLines(
                view = view,
                visible = flatRibbons(lift(lines.visible.flatMap(dash::solid), LINE_LIFT), normal, VISIBLE_WIDTH, squareCaps = true),
                hidden = flatRibbons(lift(lines.hidden.flatMap { dash.dashed(it, LineDashUseCase.HIDDEN) }, LINE_LIFT), normal, THIN_WIDTH, squareCaps = false),
                center = flatRibbons(lift(lines.center.flatMap { dash.dashed(it, LineDashUseCase.CENTER) }, LINE_LIFT), normal, THIN_WIDTH, squareCaps = false),
                border = flatRibbons(lift(dash.solid(planeOutline(view, box)), BORDER_LIFT), normal, BORDER_WIDTH, squareCaps = true),
            )
        }
        val rays = ViewKind.entries.associateWith { view ->
            crossRibbons(boxLayout.rays(drawing.mesh, view, box).map { Segment3(it.from, it.to) }, RAY_WIDTH)
        }
        return BoxSceneSpec(
            partId = drawing.part.id,
            box = box,
            mesh = drawing.mesh,
            planes = planes,
            rays = rays,
            edges = crossRibbons(drawing.mesh.sharpEdges, EDGE_WIDTH),
        )
    }

    /** Where a drawing point of [view] lies on its plane, lifted by [lift] toward the part. */
    fun onPlane(p: Vec2, view: ViewKind, box: BoxLayout, lift: Double = 0.0): Vec3 = when (view) {
        ViewKind.FRONT -> Vec3(p.u, p.v, box.backZ + lift)
        ViewKind.TOP -> Vec3(p.u, box.bottomY + lift, 0.0 - p.v)
        ViewKind.LEFT -> Vec3(box.rightX - lift, p.v, p.u)
    }

    /** The rectangle of a plane in the drawing coordinates of its view. */
    fun planeOutline(view: ViewKind, box: BoxLayout): Polyline2 {
        val (u0, u1, v0, v1) = when (view) {
            ViewKind.FRONT -> listOf(box.minX, box.rightX, box.bottomY, box.topY)
            ViewKind.TOP -> listOf(box.minX, box.rightX, -box.frontZ, -box.backZ)
            ViewKind.LEFT -> listOf(box.backZ, box.frontZ, box.bottomY, box.topY)
        }
        return Polyline2(listOf(Vec2(u0, v0), Vec2(u1, v0), Vec2(u1, v1), Vec2(u0, v1)), closed = true)
    }

    /** One quad per segment, lying in the plane with the given [normal]. */
    fun flatRibbons(segments: List<Segment3>, normal: Vec3, width: Double, squareCaps: Boolean): RibbonMesh {
        val builder = RibbonBuilder(segments.size)
        for (s in segments) {
            if (s.length() < MIN_LENGTH) continue
            val d = (s.b - s.a).unit()
            val side = normal.cross(d).unit() * (width / 2)
            val cap = if (squareCaps) d * (width / 2) else Vec3.ZERO
            builder.quad(s.a - cap + side, s.a - cap - side, s.b + cap - side, s.b + cap + side)
        }
        return builder.build()
    }

    /** Two perpendicular quads per segment, so a line in the air is visible from every side. */
    fun crossRibbons(segments: List<Segment3>, width: Double): RibbonMesh {
        val builder = RibbonBuilder(segments.size * 2)
        for (s in segments) {
            if (s.length() < MIN_LENGTH) continue
            val d = (s.b - s.a).unit()
            val helper = if (abs(d.x) < 0.9) Vec3.UNIT_X else Vec3.UNIT_Y
            val n1 = d.cross(helper).unit() * (width / 2)
            val n2 = d.cross(n1).unit() * (width / 2)
            builder.quad(s.a + n1, s.a - n1, s.b - n1, s.b + n1)
            builder.quad(s.a + n2, s.a - n2, s.b - n2, s.b + n2)
        }
        return builder.build()
    }

    private class RibbonBuilder(quads: Int) {
        private var positions = FloatArray(maxOf(quads, 1) * 12)
        private var count = 0
        private var minX = Double.MAX_VALUE; private var minY = Double.MAX_VALUE; private var minZ = Double.MAX_VALUE
        private var maxX = -Double.MAX_VALUE; private var maxY = -Double.MAX_VALUE; private var maxZ = -Double.MAX_VALUE

        fun quad(a: Vec3, b: Vec3, c: Vec3, d: Vec3) {
            if (count * 12 + 12 > positions.size) positions = positions.copyOf(positions.size * 2)
            var at = count * 12
            for (p in arrayOf(a, b, c, d)) {
                positions[at++] = p.x.toFloat(); positions[at++] = p.y.toFloat(); positions[at++] = p.z.toFloat()
                if (p.x < minX) minX = p.x; if (p.y < minY) minY = p.y; if (p.z < minZ) minZ = p.z
                if (p.x > maxX) maxX = p.x; if (p.y > maxY) maxY = p.y; if (p.z > maxZ) maxZ = p.z
            }
            count++
        }

        fun build(): RibbonMesh {
            if (count == 0) return RibbonMesh(FloatArray(0), IntArray(0), Vec3.ZERO, Vec3.ZERO)
            val indices = IntArray(count * 6)
            for (q in 0 until count) {
                val base = q * 4
                val at = q * 6
                indices[at] = base; indices[at + 1] = base + 1; indices[at + 2] = base + 2
                indices[at + 3] = base; indices[at + 4] = base + 2; indices[at + 5] = base + 3
            }
            return RibbonMesh(positions.copyOf(count * 12), indices, Vec3(minX, minY, minZ), Vec3(maxX, maxY, maxZ))
        }
    }

    companion object {
        /** Line widths in the box, millimetres of part space. A little heavier than on paper so they read at arm's length. */
        const val VISIBLE_WIDTH = 1.2
        const val THIN_WIDTH = 0.55
        const val BORDER_WIDTH = 0.35
        const val RAY_WIDTH = 0.35
        const val EDGE_WIDTH = 0.7

        /** How far the line work floats above its plane, so it never fights the plane for depth. */
        const val LINE_LIFT = 0.5
        const val BORDER_LIFT = 0.3
        private const val MIN_LENGTH = 1e-6
    }
}
