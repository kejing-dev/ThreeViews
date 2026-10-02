package io.embodit.threeviews.domain.geometry

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The three principal views of GB first-angle projection.
 * Drawing coordinates: u to the right, v up, in millimetres at 1:1.
 */
enum class ViewKind(val labelZh: String, val labelEn: String, val towardViewer: Vec3) {
    /** 主视图: seen from the front. u = x, v = y. */
    FRONT("主视图", "Front view", Vec3(0.0, 0.0, 1.0)),

    /** 俯视图: seen from above. u = x, v = -z, so the front of the part is at the bottom. */
    TOP("俯视图", "Top view", Vec3(0.0, 1.0, 0.0)),

    /** 左视图: seen from the left, looking toward +X. u = z, v = y, so the front of the part is on the right. */
    LEFT("左视图", "Left view", Vec3(-1.0, 0.0, 0.0));

    fun project(p: Vec3): Vec2 = when (this) {
        FRONT -> Vec2(p.x, p.y)
        TOP -> Vec2(p.x, 0.0 - p.z)
        LEFT -> Vec2(p.z, p.y)
    }

    /** Distance toward the viewer; larger is nearer. */
    fun depth(p: Vec3): Double = p.dot(towardViewer)
}

/** Connected run of straight segments in drawing coordinates. When [closed], the last point joins the first. */
data class Polyline2(val points: List<Vec2>, val closed: Boolean) {
    fun length(): Double {
        var sum = 0.0
        for (i in 0 until points.size - 1) sum += points[i].distanceTo(points[i + 1])
        if (closed && points.size > 1) sum += points[points.size - 1].distanceTo(points[0])
        return sum
    }

    /** The individual segments, including the closing one. */
    fun segments(): List<Pair<Vec2, Vec2>> {
        val out = ArrayList<Pair<Vec2, Vec2>>(points.size)
        for (i in 0 until points.size - 1) out.add(points[i] to points[i + 1])
        if (closed && points.size > 1) out.add(points[points.size - 1] to points[0])
        return out
    }
}

data class Bounds2(val minU: Double, val minV: Double, val maxU: Double, val maxV: Double) {
    val width: Double get() = maxU - minU
    val height: Double get() = maxV - minV
}

/** One orthographic view of a part, ready to stroke. */
data class ViewDrawing(
    val view: ViewKind,
    /** Visible outlines: continuous thick lines (粗实线). */
    val visible: List<Polyline2>,
    /** Hidden outlines: dashed lines (细虚线). Never overlaps [visible]. */
    val hidden: List<Polyline2>,
    /** Centre lines of cylindrical features: thin chain lines (细点画线). May extend beyond [bounds]. */
    val center: List<Polyline2>,
    /** Bounding box of the part in this view's drawing coordinates. */
    val bounds: Bounds2,
)

/**
 * Computes exact hidden-line drawings of a part.
 *
 * Stateless: every call works on its own data, so it is safe to call from any thread.
 */
object ViewProjector {

    /** How far a centre line runs past the feature it belongs to, in millimetres. */
    const val CENTER_LINE_OVERHANG: Double = 3.0

    private const val FACING = 1e-6        // |normal . towardViewer| below this is edge-on
    private const val CLIP = 1e-7          // outline tolerance when clipping an edge against a face
    private const val DEPTH = 1e-6         // a face hides an edge only when nearer by more than this
    private const val MIN_LENGTH = 1e-4    // drawing pieces shorter than this are noise
    private const val LINE = 1e-5          // segments this close to a line lie on it
    private const val JOIN = 2e-5          // endpoints this close are the same point

    /** Builds the solid of [part] and draws one view. For all three views prefer [computeDrawing]. */
    fun project(part: PartDefinition, view: ViewKind): ViewDrawing =
        project(PartGeometry.of(part.build()), part.axes, view)

    /** Draws one view of an already analysed solid. */
    fun project(geometry: PartGeometry, axes: List<AxisFeature>, view: ViewKind): ViewDrawing {
        val toward = view.towardViewer
        val occluders = buildOccluders(geometry, view)

        // a. + b. Candidate edges: all sharp edges, plus smooth edges on the silhouette of this view.
        val candidates = ArrayList<Segment3>(geometry.sharpEdges.size + 64)
        candidates.addAll(geometry.sharpEdges)
        for (e in geometry.smoothEdges) {
            val front1 = e.normal1.dot(toward) > FACING
            val front2 = e.normal2.dot(toward) > FACING
            if (front1 != front2) candidates.add(Segment3(e.a, e.b))
        }

        // c. Exact visibility of every candidate.
        val pieces = ArrayList<Piece>(candidates.size * 2)
        for (edge in candidates) classify(edge, view, occluders, pieces)

        // d. Merge collinear pieces; a visible line wins over a hidden one.
        val visibleSegments = ArrayList<Seg2>()
        val hiddenSegments = ArrayList<Seg2>()
        mergeLines(pieces, visibleSegments, hiddenSegments)

        // f. Centre lines.
        val centerPieces = ArrayList<Piece>()
        for (axis in axes) addCenterLines(axis, view, centerPieces)
        val centerSegments = ArrayList<Seg2>()
        mergeLines(centerPieces, centerSegments, ArrayList())

        // Bounds.
        var minU = Double.POSITIVE_INFINITY
        var minV = Double.POSITIVE_INFINITY
        var maxU = Double.NEGATIVE_INFINITY
        var maxV = Double.NEGATIVE_INFINITY
        for (cx in 0..1) for (cy in 0..1) for (cz in 0..1) {
            val p = view.project(
                Vec3(
                    if (cx == 0) geometry.boundsMin.x else geometry.boundsMax.x,
                    if (cy == 0) geometry.boundsMin.y else geometry.boundsMax.y,
                    if (cz == 0) geometry.boundsMin.z else geometry.boundsMax.z,
                ),
            )
            minU = min(minU, p.u); maxU = max(maxU, p.u)
            minV = min(minV, p.v); maxV = max(maxV, p.v)
        }

        return ViewDrawing(
            view = view,
            visible = chain(visibleSegments),          // e.
            hidden = chain(hiddenSegments),
            center = centerSegments.map { Polyline2(listOf(clean(it.a), clean(it.b)), closed = false) },
            bounds = Bounds2(cleanCoordinate(minU), cleanCoordinate(minV), cleanCoordinate(maxU), cleanCoordinate(maxV)),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Visibility
    // ---------------------------------------------------------------------------------------------

    /** A front-facing polygon projected into the view, with what is needed to test edges against it. */
    private class Occluder(
        val count: Int,
        /** Inward unit normals of the projected outline's edges and their offsets: inside when nu*u + nv*v >= c. */
        val nu: DoubleArray,
        val nv: DoubleArray,
        val c: DoubleArray,
        val minU: Double,
        val maxU: Double,
        val minV: Double,
        val maxV: Double,
        val maxDepth: Double,
        val normal: Vec3,
        val w: Double,
        val normalDotToward: Double,
    )

    private class Piece(val a: Vec2, val b: Vec2, val hidden: Boolean)
    private class Seg2(val a: Vec2, val b: Vec2)

    private fun buildOccluders(geometry: PartGeometry, view: ViewKind): List<Occluder> {
        val toward = view.towardViewer
        val out = ArrayList<Occluder>()
        for (poly in geometry.polygons) {
            val facing = poly.plane.normal.dot(toward)
            // A closed solid is fully covered by its front faces; edge-on faces cover no area.
            if (facing <= FACING) continue
            val n = poly.vertices.size
            val us = DoubleArray(n)
            val vs = DoubleArray(n)
            var minU = Double.POSITIVE_INFINITY
            var minV = Double.POSITIVE_INFINITY
            var maxU = Double.NEGATIVE_INFINITY
            var maxV = Double.NEGATIVE_INFINITY
            var maxDepth = Double.NEGATIVE_INFINITY
            for (i in 0 until n) {
                val p = view.project(poly.vertices[i])
                us[i] = p.u
                vs[i] = p.v
                minU = min(minU, p.u); maxU = max(maxU, p.u)
                minV = min(minV, p.v); maxV = max(maxV, p.v)
                maxDepth = max(maxDepth, view.depth(poly.vertices[i]))
            }
            var twiceArea = 0.0
            for (i in 0 until n) {
                val j = (i + 1) % n
                twiceArea += us[i] * vs[j] - us[j] * vs[i]
            }
            val sign = if (twiceArea >= 0) 1.0 else -1.0
            val nu = DoubleArray(n)
            val nv = DoubleArray(n)
            val c = DoubleArray(n)
            var edges = 0
            for (i in 0 until n) {
                val j = (i + 1) % n
                val eu = us[j] - us[i]
                val ev = vs[j] - vs[i]
                val len = sqrt(eu * eu + ev * ev)
                if (len < 1e-12) continue
                nu[edges] = -ev / len * sign
                nv[edges] = eu / len * sign
                c[edges] = nu[edges] * us[i] + nv[edges] * vs[i]
                edges++
            }
            if (edges < 3) continue
            out.add(
                Occluder(edges, nu, nv, c, minU, maxU, minV, maxV, maxDepth, poly.plane.normal, poly.plane.w, facing),
            )
        }
        return out
    }

    /**
     * Splits one 3D edge into visible and hidden pieces.
     *
     * For each front-facing polygon the projected edge is clipped against the polygon's convex
     * outline, giving a parameter interval. Along the edge the distance by which the polygon's plane
     * is nearer the viewer is linear, so the part of that interval where the polygon is strictly in
     * front is again an interval. The union of those is hidden. A face that contains the edge has a
     * zero distance everywhere and never hides it.
     *
     * The outline clip is closed (boundary included): an edge exactly under the seam between two
     * coplanar fragments is hidden by both. An edge exactly under a face's true outline is reported
     * hidden too; that outline is itself a candidate on the same drawing line and decides the result
     * in the merge.
     */
    private fun classify(edge: Segment3, view: ViewKind, occluders: List<Occluder>, out: MutableList<Piece>) {
        val a = view.project(edge.a)
        val b = view.project(edge.b)
        val du = b.u - a.u
        val dv = b.v - a.v
        val length = sqrt(du * du + dv * dv)
        if (length < MIN_LENGTH) return // b. the edge projects to a point

        val segMinU = min(a.u, b.u) - CLIP
        val segMaxU = max(a.u, b.u) + CLIP
        val segMinV = min(a.v, b.v) - CLIP
        val segMaxV = max(a.v, b.v) + CLIP
        val edgeMinDepth = min(view.depth(edge.a), view.depth(edge.b))
        val delta = edge.b - edge.a

        val hidden = ArrayList<DoubleArray>()
        for (occ in occluders) {
            if (occ.maxU < segMinU || occ.minU > segMaxU || occ.maxV < segMinV || occ.minV > segMaxV) continue
            if (occ.maxDepth <= edgeMinDepth + DEPTH) continue

            var t0 = 0.0
            var t1 = 1.0
            var inside = true
            for (k in 0 until occ.count) {
                val f0 = occ.nu[k] * a.u + occ.nv[k] * a.v - occ.c[k]
                val fd = occ.nu[k] * du + occ.nv[k] * dv
                // Inside the (slightly grown) outline when f0 + t * fd >= -CLIP.
                if (abs(fd) < 1e-12) {
                    if (f0 < -CLIP) { inside = false; break }
                } else {
                    val tc = (-CLIP - f0) / fd
                    if (fd > 0) { if (tc > t0) t0 = tc } else { if (tc < t1) t1 = tc }
                    if (t0 > t1) { inside = false; break }
                }
            }
            if (!inside) continue

            // Distance from the edge point to the polygon's plane, measured toward the viewer.
            val s0 = (occ.w - occ.normal.dot(edge.a)) / occ.normalDotToward
            val ds = -occ.normal.dot(delta) / occ.normalDotToward
            val sa = s0 + ds * t0
            val sb = s0 + ds * t1
            val aFront = sa > DEPTH
            val bFront = sb > DEPTH
            if (!aFront && !bFront) continue
            if (aFront && bFront) {
                hidden.add(doubleArrayOf(t0, t1))
            } else {
                val tx = t0 + (DEPTH - sa) / (sb - sa) * (t1 - t0)
                hidden.add(if (aFront) doubleArrayOf(t0, tx) else doubleArrayOf(tx, t1))
            }
        }

        if (hidden.isEmpty()) {
            out.add(Piece(a, b, hidden = false))
            return
        }
        // Union; drop hidden slivers (a nearer corner grazing the edge), then bridge visible slivers.
        val noise = MIN_LENGTH / length
        val solid = unionIntervals(hidden, 1e-12).filter { it[1] - it[0] >= noise }
        val merged = unionIntervals(solid, noise).map { doubleArrayOf(max(0.0, it[0]), min(1.0, it[1])) }
        if (merged.isEmpty()) {
            out.add(Piece(a, b, hidden = false))
            return
        }
        if (merged[0][0] < noise) merged[0][0] = 0.0
        if (1.0 - merged[merged.size - 1][1] < noise) merged[merged.size - 1][1] = 1.0
        fun at(t: Double) = Vec2(a.u + du * t, a.v + dv * t)
        var cursor = 0.0
        for (iv in merged) {
            if (iv[0] > cursor) out.add(Piece(at(cursor), at(iv[0]), hidden = false))
            out.add(Piece(at(iv[0]), at(iv[1]), hidden = true))
            cursor = iv[1]
        }
        if (cursor < 1.0) out.add(Piece(at(cursor), at(1.0), hidden = false))
    }

    // ---------------------------------------------------------------------------------------------
    // Merging and chaining in the drawing plane
    // ---------------------------------------------------------------------------------------------

    /**
     * Groups collinear pieces by drawing line, unions the visible intervals, and keeps hidden
     * intervals only where no visible interval covers them (GB: a visible line takes precedence).
     */
    private fun mergeLines(pieces: List<Piece>, visibleOut: MutableList<Seg2>, hiddenOut: MutableList<Seg2>) {
        class Line(val origin: Vec2, val dir: Vec2) {
            val visible = ArrayList<DoubleArray>()
            val hidden = ArrayList<DoubleArray>()
            fun contains(p: Vec2): Boolean = abs(dir.cross(p - origin)) < LINE
            fun accepts(piece: Piece, d: Vec2): Boolean =
                abs(dir.cross(d)) < 1e-3 && contains(piece.a) && contains(piece.b)
        }

        val sorted = pieces.sortedByDescending { it.a.distanceTo(it.b) }
        val lines = ArrayList<Line>()
        val buckets = HashMap<Long, MutableList<Line>>()
        for (piece in sorted) {
            val len = piece.a.distanceTo(piece.b)
            if (len < 1e-7) continue
            var d = (piece.b - piece.a) * (1.0 / len)
            val lead = if (abs(d.u) > 1e-9) d.u else d.v
            if (lead < 0) d = d * -1.0
            val key = Math.round(d.u * 1e3) * 4_003L + Math.round(d.v * 1e3)
            var line = buckets[key]?.firstOrNull { it.accepts(piece, d) }
            if (line == null) line = lines.firstOrNull { it.accepts(piece, d) }
            if (line == null) {
                line = Line(piece.a, d)
                lines.add(line)
                buckets.getOrPut(key) { ArrayList(2) }.add(line)
            }
            val t0 = (piece.a - line.origin).dot(line.dir)
            val t1 = (piece.b - line.origin).dot(line.dir)
            (if (piece.hidden) line.hidden else line.visible).add(doubleArrayOf(min(t0, t1), max(t0, t1)))
        }

        for (line in lines) {
            val visible = unionIntervals(line.visible, 1e-5)
            for (iv in visible) {
                if (iv[1] - iv[0] >= MIN_LENGTH) {
                    visibleOut.add(Seg2(line.origin + line.dir * iv[0], line.origin + line.dir * iv[1]))
                }
            }
            for (iv in unionIntervals(line.hidden, 1e-5)) {
                // Subtract the visible intervals.
                var start = iv[0]
                val end = iv[1]
                for (vis in visible) {
                    if (vis[1] <= start) continue
                    if (vis[0] >= end) break
                    if (vis[0] - start >= MIN_LENGTH) {
                        hiddenOut.add(Seg2(line.origin + line.dir * start, line.origin + line.dir * vis[0]))
                    }
                    start = max(start, vis[1])
                }
                if (end - start >= MIN_LENGTH) {
                    hiddenOut.add(Seg2(line.origin + line.dir * start, line.origin + line.dir * end))
                }
            }
        }
    }

    /** Chains segments that share endpoints into polylines; rings come out closed. */
    private fun chain(segments: List<Seg2>): List<Polyline2> {
        if (segments.isEmpty()) return emptyList()

        // Cluster endpoints into nodes.
        val nodes = ArrayList<Vec2>()
        val grid = HashMap<Long, MutableList<Int>>()
        val cell = JOIN * 4
        fun cellKey(x: Long, y: Long): Long = x * 1_000_003L + y
        fun nodeOf(p: Vec2): Int {
            val kx = Math.round(p.u / cell)
            val ky = Math.round(p.v / cell)
            for (dx in -1..1) for (dy in -1..1) {
                val bucket = grid[cellKey(kx + dx, ky + dy)] ?: continue
                for (id in bucket) if (nodes[id].distanceTo(p) < JOIN) return id
            }
            nodes.add(p)
            grid.getOrPut(cellKey(kx, ky)) { ArrayList(1) }.add(nodes.size - 1)
            return nodes.size - 1
        }

        val ends = Array(segments.size) { i -> intArrayOf(nodeOf(segments[i].a), nodeOf(segments[i].b)) }
        val incident = Array(nodes.size) { ArrayList<Int>(2) }
        val kept = ArrayList<Int>()
        for (i in segments.indices) {
            if (ends[i][0] == ends[i][1]) continue
            kept.add(i)
            incident[ends[i][0]].add(i)
            incident[ends[i][1]].add(i)
        }

        val used = BooleanArray(segments.size)
        val out = ArrayList<Polyline2>()

        fun walk(startNode: Int, firstSegment: Int) {
            val points = ArrayList<Vec2>()
            points.add(clean(nodes[startNode]))
            var node = startNode
            var seg = firstSegment
            var closed = false
            while (true) {
                used[seg] = true
                node = if (ends[seg][0] == node) ends[seg][1] else ends[seg][0]
                if (node == startNode && incident[node].size == 2) {
                    closed = true
                    break
                }
                points.add(clean(nodes[node]))
                if (incident[node].size != 2) break
                val next = if (incident[node][0] == seg) incident[node][1] else incident[node][0]
                if (used[next]) break
                seg = next
            }
            out.add(Polyline2(points, closed))
        }

        // Open chains start at nodes that are an end or a junction.
        for (node in nodes.indices) {
            if (incident[node].size == 2) continue
            for (seg in incident[node]) if (!used[seg]) walk(node, seg)
        }
        // What is left are rings.
        for (seg in kept) if (!used[seg]) walk(ends[seg][0], seg)
        return out
    }

    // ---------------------------------------------------------------------------------------------
    // Centre lines
    // ---------------------------------------------------------------------------------------------

    private fun addCenterLines(axis: AxisFeature, view: ViewKind, out: MutableList<Piece>) {
        val direction = (axis.end - axis.start).unit()
        if (abs(direction.dot(view.towardViewer)) > 1.0 - 1e-9) {
            // Seen along the axis: a cross through the centre of the circle.
            val centre = view.project(axis.start)
            val arm = axis.radius + CENTER_LINE_OVERHANG
            out.add(Piece(Vec2(centre.u - arm, centre.v), Vec2(centre.u + arm, centre.v), hidden = false))
            out.add(Piece(Vec2(centre.u, centre.v - arm), Vec2(centre.u, centre.v + arm), hidden = false))
        } else {
            // Seen from the side: the axis itself, running past both ends of the feature.
            val a = view.project(axis.start)
            val b = view.project(axis.end)
            val len = a.distanceTo(b)
            if (len < MIN_LENGTH) return
            val d = (b - a) * (1.0 / len)
            out.add(Piece(a - d * CENTER_LINE_OVERHANG, b + d * CENTER_LINE_OVERHANG, hidden = false))
        }
    }

    /** Rounds away sub-nanometre noise and turns -0.0 into 0.0 so results print and compare cleanly. */
    private fun clean(p: Vec2): Vec2 = Vec2(cleanCoordinate(p.u), cleanCoordinate(p.v))

    private fun cleanCoordinate(x: Double): Double = Math.round(x * 1e9) / 1e9 + 0.0
}
