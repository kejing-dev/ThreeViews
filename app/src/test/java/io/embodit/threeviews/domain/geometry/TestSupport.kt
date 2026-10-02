package io.embodit.threeviews.domain.geometry

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Default tolerance for drawing lengths and coordinates, millimetres. */
internal const val TOL = 1e-3

internal typealias Seg = Pair<Vec2, Vec2>

internal fun List<Polyline2>.totalLength(): Double = sumOf { it.length() }

internal fun List<Polyline2>.allSegments(): List<Seg> = flatMap { it.segments() }

internal fun near(p: Vec2, u: Double, v: Double, tol: Double = TOL): Boolean =
    abs(p.u - u) <= tol && abs(p.v - v) <= tol

/** True when one of the straight segments runs between the two given points (either direction). */
internal fun List<Polyline2>.hasSegment(u0: Double, v0: Double, u1: Double, v1: Double): Boolean =
    allSegments().any { (p, q) ->
        (near(p, u0, v0) && near(q, u1, v1)) || (near(p, u1, v1) && near(q, u0, v0))
    }

/** Length over which two segments lie on top of each other; 0 when they are not collinear. */
internal fun overlapLength(a: Seg, b: Seg): Double {
    val d = a.second - a.first
    val len = d.length()
    if (len < 1e-9) return 0.0
    val dir = d * (1.0 / len)
    if (abs(dir.cross(b.first - a.first)) > 1e-6 || abs(dir.cross(b.second - a.first)) > 1e-6) return 0.0
    val t0 = dir.dot(b.first - a.first)
    val t1 = dir.dot(b.second - a.first)
    return max(0.0, min(len, max(t0, t1)) - max(0.0, min(t0, t1)))
}

/** Area of the regular polygon that approximates a circle of this radius. */
internal fun polygonCircleArea(radius: Double, segments: Int = DEFAULT_CYLINDER_SEGMENTS): Double =
    0.5 * segments * radius * radius * sin(2.0 * Math.PI / segments)

/** Perimeter of the regular polygon that approximates a circle of this radius. */
internal fun polygonCirclePerimeter(radius: Double, segments: Int = DEFAULT_CYLINDER_SEGMENTS): Double =
    2.0 * segments * radius * sin(Math.PI / segments)

/** The closed polylines whose points all lie on the circle with this centre and radius. */
internal fun List<Polyline2>.closedCircles(cu: Double, cv: Double, radius: Double): List<Polyline2> =
    filter { line ->
        line.closed && line.points.size >= 8 &&
            line.points.all { abs(it.distanceTo(Vec2(cu, cv)) - radius) < 1e-6 }
    }

internal fun box(x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double): Solid =
    box(Vec3(x0, y0, z0), Vec3(x1, y1, z1))
