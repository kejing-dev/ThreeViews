package io.embodit.threeviews.domain.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Number of flat facets used to approximate a cylinder. Divisible by 4 so the quadrant points are vertices. */
const val DEFAULT_CYLINDER_SEGMENTS: Int = 48

/** Axis-aligned box from [min] to [max]. */
fun box(min: Vec3, max: Vec3): Solid {
    require(max.x > min.x && max.y > min.y && max.z > min.z) { "box: max must exceed min on every axis" }
    fun corner(i: Int) = Vec3(
        if (i and 1 != 0) max.x else min.x,
        if (i and 2 != 0) max.y else min.y,
        if (i and 4 != 0) max.z else min.z,
    )
    val faces = listOf(
        intArrayOf(0, 4, 6, 2) to Vec3(-1.0, 0.0, 0.0),
        intArrayOf(1, 3, 7, 5) to Vec3(1.0, 0.0, 0.0),
        intArrayOf(0, 1, 5, 4) to Vec3(0.0, -1.0, 0.0),
        intArrayOf(2, 6, 7, 3) to Vec3(0.0, 1.0, 0.0),
        intArrayOf(0, 2, 3, 1) to Vec3(0.0, 0.0, -1.0),
        intArrayOf(4, 5, 7, 6) to Vec3(0.0, 0.0, 1.0),
    )
    return Solid(
        faces.map { (indices, normal) ->
            val vertices = indices.map { corner(it) }
            Polygon3(vertices, Plane(normal, normal.dot(vertices[0])))
        },
    )
}

/**
 * Right circular cylinder approximated by a regular prism with [segments] facets, inscribed in the
 * true circle (the vertices lie on the circle of the given [radius]).
 * The axis must be parallel to X, Y or Z. Vertex 0 of the ring sits on a principal direction, so with
 * a segment count divisible by 4 the four extreme generators are real edges of the prism.
 */
fun cylinder(
    axisStart: Vec3,
    axisEnd: Vec3,
    radius: Double,
    segments: Int = DEFAULT_CYLINDER_SEGMENTS,
): Solid {
    require(radius > 0.0) { "cylinder: radius must be positive" }
    require(segments >= 3) { "cylinder: at least three segments" }
    val delta = axisEnd - axisStart
    val nonZero = listOf(delta.x, delta.y, delta.z).count { abs(it) > 1e-12 }
    require(nonZero == 1) { "cylinder: the axis must be parallel to X, Y or Z" }

    // Orient the axis along the positive direction; (e1, e2, d) is a right-handed frame.
    val d: Vec3
    val e1: Vec3
    val e2: Vec3
    when {
        abs(delta.x) > 1e-12 -> { d = Vec3.UNIT_X; e1 = Vec3.UNIT_Y; e2 = Vec3.UNIT_Z }
        abs(delta.y) > 1e-12 -> { d = Vec3.UNIT_Y; e1 = Vec3.UNIT_X; e2 = -Vec3.UNIT_Z }
        else -> { d = Vec3.UNIT_Z; e1 = Vec3.UNIT_X; e2 = Vec3.UNIT_Y }
    }
    val forward = delta.dot(d) > 0
    val s = if (forward) axisStart else axisEnd
    val e = if (forward) axisEnd else axisStart

    fun radial(angleIndex: Double): Vec3 {
        val angle = 2.0 * PI * angleIndex / segments
        return e1 * snapUnit(cos(angle)) + e2 * snapUnit(sin(angle))
    }

    val ringStart = ArrayList<Vec3>(segments)
    val ringEnd = ArrayList<Vec3>(segments)
    for (i in 0 until segments) {
        val r = radial(i.toDouble()) * radius
        ringStart.add(s + r)
        ringEnd.add(e + r)
    }

    val polygons = ArrayList<Polygon3>(segments + 2)
    polygons.add(Polygon3(ringStart.reversed(), Plane(-d, -d.dot(s))))
    polygons.add(Polygon3(ringEnd.toList(), Plane(d, d.dot(e))))
    for (i in 0 until segments) {
        val j = (i + 1) % segments
        val normal = radial(i + 0.5)
        val quad = listOf(ringStart[i], ringStart[j], ringEnd[j], ringEnd[i])
        polygons.add(Polygon3(quad, Plane(normal, normal.dot(ringStart[i]))))
    }
    return Solid(polygons)
}

/**
 * Prism with a convex [profile] in the XY plane (either winding), extruded from [zMin] to [zMax].
 */
fun prism(profile: List<Pair<Double, Double>>, zMin: Double, zMax: Double): Solid {
    require(profile.size >= 3) { "prism: the profile needs at least three points" }
    require(zMax > zMin) { "prism: zMax must exceed zMin" }
    var twiceArea = 0.0
    for (i in profile.indices) {
        val (x0, y0) = profile[i]
        val (x1, y1) = profile[(i + 1) % profile.size]
        twiceArea += x0 * y1 - x1 * y0
    }
    require(abs(twiceArea) > 1e-12) { "prism: degenerate profile" }
    val ccw = if (twiceArea > 0) profile else profile.reversed()
    for (i in ccw.indices) {
        val (x0, y0) = ccw[i]
        val (x1, y1) = ccw[(i + 1) % ccw.size]
        val (x2, y2) = ccw[(i + 2) % ccw.size]
        require((x1 - x0) * (y2 - y1) - (y1 - y0) * (x2 - x1) > 0) { "prism: the profile must be strictly convex" }
    }

    val backRing = ccw.map { (x, y) -> Vec3(x, y, zMin) }
    val frontRing = ccw.map { (x, y) -> Vec3(x, y, zMax) }
    val polygons = ArrayList<Polygon3>(ccw.size + 2)
    polygons.add(Polygon3(backRing.reversed(), Plane(Vec3(0.0, 0.0, -1.0), -zMin)))
    polygons.add(Polygon3(frontRing, Plane(Vec3(0.0, 0.0, 1.0), zMax)))
    for (i in ccw.indices) {
        val j = (i + 1) % ccw.size
        val dx = ccw[j].first - ccw[i].first
        val dy = ccw[j].second - ccw[i].second
        val len = sqrt(dx * dx + dy * dy)
        val normal = Vec3(dy / len, -dx / len, 0.0)
        val quad = listOf(backRing[i], backRing[j], frontRing[j], frontRing[i])
        polygons.add(Polygon3(quad, Plane(normal, normal.dot(backRing[i]))))
    }
    return Solid(polygons)
}

/** Removes the rounding noise of cos/sin at the quadrant angles (cos 90 deg is 6e-17, not 0). */
private fun snapUnit(value: Double): Double = when {
    abs(value) < 1e-14 -> 0.0
    abs(value - 1.0) < 1e-14 -> 1.0
    abs(value + 1.0) < 1e-14 -> -1.0
    else -> value
}
