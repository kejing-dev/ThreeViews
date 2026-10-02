package io.embodit.threeviews.domain.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** A crease between two faces that meet at less than [PartGeometry.SMOOTH_ANGLE_DEG]; drawn only as a silhouette. */
internal class SmoothEdge(val a: Vec3, val b: Vec3, val normal1: Vec3, val normal2: Vec3)

/**
 * View-independent analysis of a CSG result: welded polygons, T-junctions and feature edges.
 *
 * BSP booleans return a polygon soup: faces are cut into coplanar fragments and neighbouring
 * fragments do not share vertices (T-junctions). The analysis therefore never relies on shared
 * vertices. Every polygon edge is first cut at each vertex of any polygon that lies on it, so the
 * neighbourhood is constant along each resulting sub-edge, and each sub-edge is then classified
 * from its midpoint:
 *  - internal: a coplanar, same-facing polygon continues just across it. Dropped;
 *  - sharp: no neighbouring face, or a neighbouring face whose normal differs by 20 degrees or more;
 *  - smooth: only neighbours within 20 degrees (cylinder facets, tangent joins).
 *
 * Immutable after construction.
 */
class PartGeometry private constructor(
    /** Cleaned polygons of the solid, vertices welded. */
    val polygons: List<Polygon3>,
    /** Per polygon: its boundary loop including the T-junction vertices of neighbouring polygons. */
    internal val outlines: List<List<Vec3>>,
    /** View-independent feature edges, collinear pieces merged into single segments. */
    val sharpEdges: List<Segment3>,
    internal val smoothEdges: List<SmoothEdge>,
    val boundsMin: Vec3,
    val boundsMax: Vec3,
) {
    companion object {
        /** Faces whose normals differ by less than this are a smooth surface, not a drawn edge. */
        const val SMOOTH_ANGLE_DEG: Double = 20.0

        private const val WELD = 1e-6          // vertices closer than this are the same vertex
        private const val ON_EDGE = 1e-5       // a point this close to an edge lies on it
        private const val PROBE = 1e-4         // how far across an edge the coplanar probe reaches
        private const val MIN_EDGE = 1e-7      // shorter sub-edges are ignored

        fun of(solid: Solid): PartGeometry = Builder(solid).build()
    }

    private class Builder(solid: Solid) {
        private val unique = ArrayList<Vec3>()
        private val polys: List<Polygon3>

        init {
            // Weld vertices and drop degenerate fragments.
            val index = HashMap<Long, MutableList<Vec3>>()
            fun weld(v: Vec3): Vec3 {
                val kx = Math.round(v.x / (WELD * 4))
                val ky = Math.round(v.y / (WELD * 4))
                val kz = Math.round(v.z / (WELD * 4))
                for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                    val bucket = index[cellKey(kx + dx, ky + dy, kz + dz)] ?: continue
                    for (c in bucket) {
                        if (abs(c.x - v.x) < WELD && abs(c.y - v.y) < WELD && abs(c.z - v.z) < WELD) return c
                    }
                }
                index.getOrPut(cellKey(kx, ky, kz)) { ArrayList(1) }.add(v)
                unique.add(v)
                return v
            }

            val cleaned = ArrayList<Polygon3>(solid.polygons.size)
            for (p in solid.polygons) {
                val vs = ArrayList<Vec3>(p.vertices.size)
                for (v in p.vertices) {
                    val w = weld(v)
                    if (vs.isEmpty() || vs[vs.size - 1] !== w) vs.add(w)
                }
                while (vs.size > 1 && vs[0] === vs[vs.size - 1]) vs.removeAt(vs.size - 1)
                if (vs.size < 3) continue
                val poly = Polygon3(vs, p.plane)
                if (poly.area() < 1e-9) continue
                cleaned.add(poly)
            }
            polys = cleaned
        }

        private fun cellKey(x: Long, y: Long, z: Long): Long =
            (x * 73856093L) xor (y * 19349663L) xor (z * 83492791L)

        fun build(): PartGeometry {
            val n = polys.size

            // Bounding boxes.
            val bbMin = Array(n) { DoubleArray(3) }
            val bbMax = Array(n) { DoubleArray(3) }
            var gMin = Vec3(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
            var gMax = Vec3(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY)
            for (i in 0 until n) {
                var x0 = Double.POSITIVE_INFINITY; var y0 = x0; var z0 = x0
                var x1 = Double.NEGATIVE_INFINITY; var y1 = x1; var z1 = x1
                for (v in polys[i].vertices) {
                    x0 = min(x0, v.x); y0 = min(y0, v.y); z0 = min(z0, v.z)
                    x1 = max(x1, v.x); y1 = max(y1, v.y); z1 = max(z1, v.z)
                }
                bbMin[i][0] = x0; bbMin[i][1] = y0; bbMin[i][2] = z0
                bbMax[i][0] = x1; bbMax[i][1] = y1; bbMax[i][2] = z1
                gMin = Vec3(min(gMin.x, x0), min(gMin.y, y0), min(gMin.z, z0))
                gMax = Vec3(max(gMax.x, x1), max(gMax.y, y1), max(gMax.z, z1))
            }

            // Vertices sorted by x, for the T-junction search.
            val sortedVertices = unique.sortedWith(compareBy<Vec3> { it.x }.thenBy { it.y }.thenBy { it.z })
            val sortedX = DoubleArray(sortedVertices.size) { sortedVertices[it].x }

            // Groups of coplanar, same-facing polygons.
            val groupOf = IntArray(n)
            val groupPlanes = ArrayList<Plane>()
            val groupMembers = ArrayList<MutableList<Int>>()
            for (i in 0 until n) {
                val plane = polys[i].plane
                var found = -1
                for (g in groupPlanes.indices) {
                    val gp = groupPlanes[g]
                    if (gp.normal.dot(plane.normal) < 1.0 - 1e-9) continue
                    if (abs(gp.w - plane.w) > 1e-4) continue
                    if (polys[i].vertices.all { abs(gp.signedDistance(it)) < ON_EDGE }) {
                        found = g
                        break
                    }
                }
                if (found < 0) {
                    found = groupPlanes.size
                    groupPlanes.add(plane)
                    groupMembers.add(ArrayList())
                }
                groupOf[i] = found
                groupMembers[found].add(i)
            }

            val cosSmooth = cos(SMOOTH_ANGLE_DEG * PI / 180.0)
            val outlines = ArrayList<List<Vec3>>(n)
            val sharp = ArrayList<Segment3>()
            val smooth = ArrayList<SmoothEdge>()
            val splitParams = ArrayList<Pair<Double, Vec3>>()

            for (i in 0 until n) {
                val poly = polys[i]
                val normal = poly.plane.normal
                val vs = poly.vertices
                val outline = ArrayList<Vec3>(vs.size)
                for (k in vs.indices) {
                    val a = vs[k]
                    val b = vs[(k + 1) % vs.size]
                    val ab = b - a
                    val length = ab.length()
                    val dir = ab / length

                    // 1. T-junctions: every welded vertex strictly inside this edge.
                    splitParams.clear()
                    val loX = min(a.x, b.x) - ON_EDGE
                    val hiX = max(a.x, b.x) + ON_EDGE
                    val loY = min(a.y, b.y) - ON_EDGE
                    val hiY = max(a.y, b.y) + ON_EDGE
                    val loZ = min(a.z, b.z) - ON_EDGE
                    val hiZ = max(a.z, b.z) + ON_EDGE
                    var idx = lowerBound(sortedX, loX)
                    while (idx < sortedX.size && sortedX[idx] <= hiX) {
                        val p = sortedVertices[idx]
                        idx++
                        if (p.y < loY || p.y > hiY || p.z < loZ || p.z > hiZ) continue
                        if (p === a || p === b) continue
                        val along = (p - a).dot(dir)
                        if (along < ON_EDGE || along > length - ON_EDGE) continue
                        val foot = a + dir * along
                        if (foot.distanceTo(p) > ON_EDGE) continue
                        splitParams.add(along to p)
                    }
                    splitParams.sortBy { it.first }

                    outline.add(a)
                    var prev = a
                    var s = 0
                    while (s <= splitParams.size) {
                        val next = if (s < splitParams.size) splitParams[s].second else b
                        if (s < splitParams.size) outline.add(next)
                        s++
                        if (prev.distanceTo(next) < MIN_EDGE) {
                            prev = next
                            continue
                        }
                        // 2. Classify the sub-edge prev -> next from its midpoint.
                        val mid = (prev + next) * 0.5
                        val outward = dir.cross(normal).unit()
                        if (!continuesCoplanar(i, mid, outward, groupMembers[groupOf[i]], bbMin, bbMax)) {
                            var hasNeighbour = false
                            var isSharp = false
                            var neighbourNormal: Vec3? = null
                            for (j in 0 until n) {
                                if (groupOf[j] == groupOf[i]) continue
                                if (mid.x < bbMin[j][0] - ON_EDGE || mid.x > bbMax[j][0] + ON_EDGE ||
                                    mid.y < bbMin[j][1] - ON_EDGE || mid.y > bbMax[j][1] + ON_EDGE ||
                                    mid.z < bbMin[j][2] - ON_EDGE || mid.z > bbMax[j][2] + ON_EDGE
                                ) continue
                                if (!onBoundary(polys[j], mid)) continue
                                hasNeighbour = true
                                val nj = polys[j].plane.normal
                                if (nj.dot(normal) <= cosSmooth) {
                                    isSharp = true
                                    break
                                }
                                if (neighbourNormal == null) neighbourNormal = nj
                            }
                            if (!hasNeighbour || isSharp) {
                                sharp.add(Segment3(prev, next))
                            } else {
                                smooth.add(SmoothEdge(prev, next, normal, neighbourNormal!!))
                            }
                        }
                        prev = next
                    }
                }
                outlines.add(outline)
            }

            return PartGeometry(
                polygons = polys,
                outlines = outlines,
                sharpEdges = mergeCollinear(sharp),
                smoothEdges = smooth,
                boundsMin = gMin,
                boundsMax = gMax,
            )
        }

        /**
         * True when a coplanar, same-facing polygon covers the points just across the edge,
         * i.e. mid + s * outward for small positive s.
         */
        private fun continuesCoplanar(
            self: Int,
            mid: Vec3,
            outward: Vec3,
            group: List<Int>,
            bbMin: Array<DoubleArray>,
            bbMax: Array<DoubleArray>,
        ): Boolean {
            for (j in group) {
                if (j == self) continue
                if (mid.x < bbMin[j][0] - PROBE || mid.x > bbMax[j][0] + PROBE ||
                    mid.y < bbMin[j][1] - PROBE || mid.y > bbMax[j][1] + PROBE ||
                    mid.z < bbMin[j][2] - PROBE || mid.z > bbMax[j][2] + PROBE
                ) continue
                val q = polys[j]
                val qn = q.plane.normal
                var s0 = 0.0
                var s1 = PROBE
                var inside = true
                val qv = q.vertices
                for (k in qv.indices) {
                    val qa = qv[k]
                    val qb = qv[(k + 1) % qv.size]
                    val inward = qn.cross(qb - qa).unit()
                    val f0 = inward.dot(mid - qa)
                    val fd = inward.dot(outward)
                    // Need f0 + s * fd >= -1e-7.
                    if (abs(fd) < 1e-12) {
                        if (f0 < -1e-7) { inside = false; break }
                    } else {
                        val sc = (-1e-7 - f0) / fd
                        if (fd > 0) { if (sc > s0) s0 = sc } else { if (sc < s1) s1 = sc }
                        if (s0 > s1) { inside = false; break }
                    }
                }
                if (inside && s1 > 1e-6 && s1 - s0 > 1e-7) return true
            }
            return false
        }

        /** True when [p] lies on one of the edges of [poly]. */
        private fun onBoundary(poly: Polygon3, p: Vec3): Boolean {
            val vs = poly.vertices
            for (k in vs.indices) {
                val a = vs[k]
                val b = vs[(k + 1) % vs.size]
                val ab = b - a
                val len2 = ab.lengthSquared()
                if (len2 < 1e-20) continue
                val t = ((p - a).dot(ab) / len2).coerceIn(0.0, 1.0)
                if ((a + ab * t).distanceTo(p) < ON_EDGE) return true
            }
            return false
        }

        private fun lowerBound(sorted: DoubleArray, value: Double): Int {
            var lo = 0
            var hi = sorted.size
            while (lo < hi) {
                val m = (lo + hi) ushr 1
                if (sorted[m] < value) lo = m + 1 else hi = m
            }
            return lo
        }
    }
}

/**
 * Merges collinear, touching or overlapping 3D segments into maximal segments.
 * Deterministic: the output depends only on the input order and values.
 */
internal fun mergeCollinear(segments: List<Segment3>, lineTolerance: Double = 1e-5): List<Segment3> {
    class Line(val origin: Vec3, val dir: Vec3) {
        val intervals = ArrayList<DoubleArray>()
        fun contains(p: Vec3): Boolean = (p - origin).cross(dir).lengthSquared() < lineTolerance * lineTolerance
        fun accepts(s: Segment3, d: Vec3): Boolean =
            dir.cross(d).lengthSquared() < 1e-6 && contains(s.a) && contains(s.b)
    }

    val sorted = segments.filter { it.length() > 1e-7 }.sortedByDescending { it.length() }
    val lines = ArrayList<Line>()
    val buckets = HashMap<Long, MutableList<Line>>()

    for (s in sorted) {
        var d = (s.b - s.a).unit()
        // Canonical direction: the first significant component is positive.
        val lead = if (abs(d.x) > 1e-9) d.x else if (abs(d.y) > 1e-9) d.y else d.z
        if (lead < 0) d = -d
        val key = Math.round(d.x * 1e3) * 4_000_037L + Math.round(d.y * 1e3) * 2_003L + Math.round(d.z * 1e3)
        var line = buckets[key]?.firstOrNull { it.accepts(s, d) }
        if (line == null) line = lines.firstOrNull { it.accepts(s, d) }
        if (line == null) {
            line = Line(s.a, d)
            lines.add(line)
            buckets.getOrPut(key) { ArrayList(2) }.add(line)
        }
        val t0 = (s.a - line.origin).dot(line.dir)
        val t1 = (s.b - line.origin).dot(line.dir)
        line.intervals.add(doubleArrayOf(min(t0, t1), max(t0, t1)))
    }

    val out = ArrayList<Segment3>()
    for (line in lines) {
        for (iv in unionIntervals(line.intervals, 1e-6)) {
            if (iv[1] - iv[0] < lineTolerance) continue
            out.add(Segment3(line.origin + line.dir * iv[0], line.origin + line.dir * iv[1]))
        }
    }
    return out
}

/** Union of closed intervals; intervals closer than [gap] are joined. Returns sorted, disjoint intervals. */
internal fun unionIntervals(intervals: List<DoubleArray>, gap: Double): List<DoubleArray> {
    if (intervals.isEmpty()) return emptyList()
    val sorted = intervals.sortedBy { it[0] }
    val out = ArrayList<DoubleArray>()
    var cur = doubleArrayOf(sorted[0][0], sorted[0][1])
    for (i in 1 until sorted.size) {
        val iv = sorted[i]
        if (iv[0] <= cur[1] + gap) {
            if (iv[1] > cur[1]) cur[1] = iv[1]
        } else {
            out.add(cur)
            cur = doubleArrayOf(iv[0], iv[1])
        }
    }
    out.add(cur)
    return out
}
