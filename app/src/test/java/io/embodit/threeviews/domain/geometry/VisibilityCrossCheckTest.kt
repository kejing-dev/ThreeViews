package io.embodit.threeviews.domain.geometry

import kotlin.math.abs
import kotlin.math.min
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Independent check of the hidden-line result: points sampled on the 3D feature edges are ray-cast
 * toward the viewer against the render triangles (brute force, no shared code with the projector)
 * and compared with the kind of line the projector drew at that spot.
 */
class VisibilityCrossCheckTest {

    private class Triangle(
        val a: Vec2, val b: Vec2, val c: Vec2,
        val depthA: Double, val depthB: Double, val depthC: Double,
    )

    private enum class Seen { VISIBLE, HIDDEN, UNCERTAIN }

    private val margin = 1e-3

    private fun triangles(mesh: MeshData, view: ViewKind): List<Triangle> {
        fun vertex(i: Int) = Vec3(
            mesh.positions[i * 3].toDouble(),
            mesh.positions[i * 3 + 1].toDouble(),
            mesh.positions[i * 3 + 2].toDouble(),
        )
        return (0 until mesh.triangleCount).map { t ->
            val a = vertex(mesh.indices[t * 3])
            val b = vertex(mesh.indices[t * 3 + 1])
            val c = vertex(mesh.indices[t * 3 + 2])
            Triangle(view.project(a), view.project(b), view.project(c), view.depth(a), view.depth(b), view.depth(c))
        }
    }

    /**
     * Is the point at drawing position [q] and depth [depth] covered by a nearer triangle?
     * A point exactly under the seam between two triangles grazes both outlines; it still counts as
     * hidden when the points just left and right of it (along [across]) are both clearly covered.
     */
    private fun seen(triangles: List<Triangle>, q: Vec2, depth: Double, across: Vec2): Seen {
        val direct = seen(triangles, q, depth)
        if (direct != Seen.UNCERTAIN) return direct
        val step = across * (5 * margin / across.length())
        val left = seen(triangles, q + step, depth)
        val right = seen(triangles, q - step, depth)
        return if (left == Seen.HIDDEN && right == Seen.HIDDEN) Seen.HIDDEN else Seen.UNCERTAIN
    }

    /** The same for a single ray. */
    private fun seen(triangles: List<Triangle>, q: Vec2, depth: Double): Seen {
        var uncertain = false
        for (t in triangles) {
            val den = (t.b - t.a).cross(t.c - t.a)
            if (abs(den) < 1e-9) continue // seen edge-on: covers no area
            val sign = if (den > 0) 1.0 else -1.0
            fun inside(p: Vec2, r: Vec2): Double = sign * (r - p).cross(q - p) / p.distanceTo(r)
            val clearance = min(inside(t.a, t.b), min(inside(t.b, t.c), inside(t.c, t.a)))
            if (clearance < -margin) continue
            val wb = (q - t.a).cross(t.c - t.a) / den
            val wc = (t.b - t.a).cross(q - t.a) / den
            val ahead = (1 - wb - wc) * t.depthA + wb * t.depthB + wc * t.depthC - depth
            if (ahead <= margin) continue // behind the point, or the face the point lies on
            if (clearance > margin) return Seen.HIDDEN
            uncertain = true // grazes the outline of a nearer triangle
        }
        return if (uncertain) Seen.UNCERTAIN else Seen.VISIBLE
    }

    private fun distance(q: Vec2, s: Seg): Double {
        val d = s.second - s.first
        val t = ((q - s.first).dot(d) / d.dot(d)).coerceIn(0.0, 1.0)
        return q.distanceTo(s.first + d * t)
    }

    private fun on(q: Vec2, segments: List<Seg>): Boolean = segments.any { distance(q, it) < 1e-5 }

    @Test
    fun featureEdgePoints_areDrawnWithTheKindARayCastGives() {
        val fractions = listOf(0.137, 0.31, 0.5, 0.713, 0.889)
        var totalVisible = 0
        var totalHidden = 0
        for (part in PartLibrary.parts) {
            val drawing = computeDrawing(part.id)
            for (view in drawing.views) {
                val name = "${part.id} ${view.view}"
                val tris = triangles(drawing.mesh, view.view)
                val visible = view.visible.allSegments()
                val hidden = view.hidden.allSegments()
                var checkedVisible = 0
                var checkedHidden = 0
                for (edge in drawing.mesh.sharpEdges) {
                    val a = view.view.project(edge.a)
                    val b = view.view.project(edge.b)
                    if (a.distanceTo(b) < 1e-6) continue // seen end-on
                    for (f in fractions) {
                        val p = edge.a.lerp(edge.b, f)
                        val q = view.view.project(p)
                        when (seen(tris, q, view.view.depth(p), Vec2(a.v - b.v, b.u - a.u))) {
                            Seen.VISIBLE -> {
                                assertTrue("$name: visible edge point $p is not drawn as a visible line", on(q, visible))
                                checkedVisible++
                            }
                            Seen.HIDDEN -> {
                                // Drawn dashed, unless a visible line lies on top of it.
                                assertTrue("$name: hidden edge point $p is not drawn at all", on(q, visible) || on(q, hidden))
                                checkedHidden++
                            }
                            Seen.UNCERTAIN -> assertTrue("$name: edge point $p is not drawn", on(q, visible) || on(q, hidden))
                        }
                    }
                }
                assertTrue("$name: too few visible samples", checkedVisible >= 20)
                totalVisible += checkedVisible
                totalHidden += checkedHidden
            }
        }
        println("Ray-cast cross-check: $totalVisible visible and $totalHidden hidden edge samples agreed")
        assertTrue("too few hidden samples: $totalHidden", totalHidden >= 100)
    }

    @Test
    fun drawnLines_haveAFeatureEdgeOfTheRightKindBehindThem() {
        val fractions = listOf(0.21, 0.5, 0.83)
        for (part in PartLibrary.parts) {
            val drawing = computeDrawing(part.id)
            for (view in drawing.views) {
                val name = "${part.id} ${view.view}"
                val tris = triangles(drawing.mesh, view.view)

                /** What a ray cast says about every sharp-edge point that projects onto [q]. */
                fun preimages(q: Vec2): List<Seen> {
                    val out = ArrayList<Seen>()
                    for (edge in drawing.mesh.sharpEdges) {
                        val a = view.view.project(edge.a)
                        val b = view.view.project(edge.b)
                        val d = b - a
                        val len2 = d.dot(d)
                        if (len2 < 1e-12) continue
                        val t = (q - a).dot(d) / len2
                        if (t < 0.0 || t > 1.0 || q.distanceTo(a + d * t) > 1e-6) continue
                        out.add(seen(tris, q, view.view.depth(edge.a.lerp(edge.b, t)), Vec2(-d.v, d.u)))
                    }
                    return out
                }

                var checked = 0
                for ((p0, p1) in view.visible.allSegments()) for (f in fractions) {
                    val found = preimages(p0 + (p1 - p0) * f)
                    if (found.isEmpty()) continue // a pure silhouette line of a curved surface
                    // Not every edge behind a visible line may be robustly hidden... unless a silhouette is
                    // the visible one, which no part of the library has on top of hidden sharp edges.
                    assertTrue("$name: visible line at ${p0 + (p1 - p0) * f} has only hidden edges", found.any { it != Seen.HIDDEN })
                    checked++
                }
                for ((p0, p1) in view.hidden.allSegments()) for (f in fractions) {
                    val found = preimages(p0 + (p1 - p0) * f)
                    assertTrue("$name: hidden line at ${p0 + (p1 - p0) * f} has a visible edge", found.none { it == Seen.VISIBLE })
                    checked++
                }
                assertTrue("$name: too few samples", checked >= 10)
            }
        }
    }
}
