package io.embodit.threeviews.domain.geometry

import kotlin.math.sqrt

/**
 * Point or direction in part space, millimetres.
 * Right-handed: X = length (left to right), Y = height (up), Z = width (+Z toward the front viewer).
 */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = Vec3(x * k, y * k, z * k)
    operator fun div(k: Double) = Vec3(x / k, y / k, z / k)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    fun dot(o: Vec3): Double = x * o.x + y * o.y + z * o.z

    fun cross(o: Vec3) = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x,
    )

    fun lengthSquared(): Double = x * x + y * y + z * z
    fun length(): Double = sqrt(lengthSquared())
    fun unit(): Vec3 = this / length()
    fun lerp(o: Vec3, t: Double): Vec3 = this + (o - this) * t
    fun distanceTo(o: Vec3): Double = (this - o).length()

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
        val UNIT_X = Vec3(1.0, 0.0, 0.0)
        val UNIT_Y = Vec3(0.0, 1.0, 0.0)
        val UNIT_Z = Vec3(0.0, 0.0, 1.0)
    }
}

/** Point in drawing coordinates of one view, millimetres. u runs to the right, v runs up. */
data class Vec2(val u: Double, val v: Double) {
    operator fun plus(o: Vec2) = Vec2(u + o.u, v + o.v)
    operator fun minus(o: Vec2) = Vec2(u - o.u, v - o.v)
    operator fun times(k: Double) = Vec2(u * k, v * k)

    fun dot(o: Vec2): Double = u * o.u + v * o.v

    /** z component of the 3D cross product; positive when [o] is counter-clockwise from this. */
    fun cross(o: Vec2): Double = u * o.v - v * o.u

    fun length(): Double = sqrt(u * u + v * v)
    fun distanceTo(o: Vec2): Double = (this - o).length()
}

/** Straight 3D segment, millimetres. */
data class Segment3(val a: Vec3, val b: Vec3) {
    fun length(): Double = a.distanceTo(b)
}
