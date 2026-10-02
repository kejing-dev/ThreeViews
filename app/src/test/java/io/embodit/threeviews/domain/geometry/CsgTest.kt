package io.embodit.threeviews.domain.geometry

import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CsgTest {

    private fun assertWellFormed(solid: Solid) {
        for (p in solid.polygons) {
            val n = p.plane.normal
            assertEquals("unit normal", 1.0, n.length(), 1e-9)
            for (v in p.vertices) {
                assertEquals("vertex on its plane", 0.0, p.plane.signedDistance(v), 1e-6)
            }
            // Convex and counter-clockwise seen from outside: every corner turns left about the normal.
            val vs = p.vertices
            for (i in vs.indices) {
                val a = vs[i]
                val b = vs[(i + 1) % vs.size]
                val c = vs[(i + 2) % vs.size]
                assertTrue("convex CCW corner", (b - a).cross(c - b).dot(n) > -1e-6)
            }
        }
    }

    @Test
    fun box_hasSixOutwardFacesAndTheRightVolume() {
        val solid = box(1.0, 2.0, 3.0, 5.0, 7.0, 9.0)
        assertEquals(6, solid.polygons.size)
        assertEquals(4.0 * 5.0 * 6.0, solid.volume(), 1e-9)
        assertWellFormed(solid)
        val (lo, hi) = solid.bounds()
        assertEquals(Vec3(1.0, 2.0, 3.0), lo)
        assertEquals(Vec3(5.0, 7.0, 9.0), hi)
    }

    @Test
    fun cylinder_isAnInscribedPrismWithExactQuadrantPoints() {
        for (axis in listOf(Vec3.UNIT_X, Vec3.UNIT_Y, Vec3.UNIT_Z)) {
            val start = Vec3(10.0, 20.0, 30.0)
            val solid = cylinder(start, start + axis * 12.0, 5.0)
            assertEquals(DEFAULT_CYLINDER_SEGMENTS + 2, solid.polygons.size)
            assertEquals(polygonCircleArea(5.0) * 12.0, solid.volume(), 1e-6)
            assertWellFormed(solid)
            val (lo, hi) = solid.bounds()
            // The extreme generators are vertices, so the bounding box equals that of the true cylinder.
            val across = Vec3(1.0, 1.0, 1.0) - axis
            assertEquals(start - across * 5.0, lo)
            assertEquals(start + axis * 12.0 + across * 5.0, hi)
            for (p in solid.polygons) for (v in p.vertices) {
                val radial = (v - start) - axis * (v - start).dot(axis)
                assertEquals(5.0, radial.length(), 1e-9)
            }
        }
    }

    @Test
    fun cylinder_acceptsAReversedAxis() {
        val a = cylinder(Vec3(0.0, 0.0, 0.0), Vec3(0.0, 10.0, 0.0), 4.0)
        val b = cylinder(Vec3(0.0, 10.0, 0.0), Vec3(0.0, 0.0, 0.0), 4.0)
        assertEquals(a.volume(), b.volume(), 1e-9)
        assertWellFormed(b)
    }

    @Test
    fun cylinder_rejectsASkewAxis() {
        try {
            cylinder(Vec3(0.0, 0.0, 0.0), Vec3(1.0, 1.0, 0.0), 4.0)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun prism_acceptsEitherWinding() {
        val ccw = prism(listOf(0.0 to 0.0, 30.0 to 0.0, 0.0 to 40.0), 0.0, 10.0)
        val cw = prism(listOf(0.0 to 0.0, 0.0 to 40.0, 30.0 to 0.0), 0.0, 10.0)
        assertEquals(6000.0, ccw.volume(), 1e-9)
        assertEquals(6000.0, cw.volume(), 1e-9)
        assertEquals(5, ccw.polygons.size)
        assertWellFormed(ccw)
        assertWellFormed(cw)
    }

    @Test
    fun booleans_ofTwoOverlappingCubes() {
        val a = box(0.0, 0.0, 0.0, 2.0, 2.0, 2.0)
        val b = box(1.0, 1.0, 1.0, 3.0, 3.0, 3.0)
        val union = a.union(b)
        val difference = a.subtract(b)
        val intersection = a.intersect(b)
        assertEquals(15.0, union.volume(), 1e-9)
        assertEquals(7.0, difference.volume(), 1e-9)
        assertEquals(1.0, intersection.volume(), 1e-9)
        assertWellFormed(union)
        assertWellFormed(difference)
        assertWellFormed(intersection)
        assertEquals(Vec3(1.0, 1.0, 1.0) to Vec3(2.0, 2.0, 2.0), intersection.bounds())
        // Operands are immutable.
        assertEquals(6, a.polygons.size)
        assertEquals(6, b.polygons.size)
    }

    @Test
    fun union_ofSolidsWithCoplanarFaces() {
        // The two boxes of the L-block share four face planes.
        val solid = box(0.0, 0.0, 0.0, 60.0, 15.0, 40.0).union(box(0.0, 0.0, 0.0, 15.0, 45.0, 40.0))
        assertEquals(54000.0, solid.volume(), 1e-6)
        assertWellFormed(solid)
    }

    @Test
    fun union_ofSolidsThatTouchOnAFace() {
        val solid = box(0.0, 0.0, 0.0, 10.0, 10.0, 10.0).union(box(0.0, 10.0, 0.0, 10.0, 15.0, 10.0))
        assertEquals(1500.0, solid.volume(), 1e-6)
        // The touching faces are gone.
        assertTrue(solid.polygons.none { p -> p.vertices.all { abs(it.y - 10.0) < 1e-9 } })
    }

    @Test
    fun subtract_cylinderFromBox() {
        val solid = box(0.0, 0.0, 0.0, 70.0, 14.0, 44.0)
            .subtract(cylinder(Vec3(35.0, -1.0, 22.0), Vec3(35.0, 15.0, 22.0), 11.0))
        assertEquals(70.0 * 14.0 * 44.0 - polygonCircleArea(11.0) * 14.0, solid.volume(), 1e-6)
        assertWellFormed(solid)
    }

    @Test
    fun intersect_boxWithCylinder() {
        val solid = box(0.0, 0.0, 0.0, 10.0, 10.0, 10.0)
            .intersect(cylinder(Vec3(0.0, -5.0, 0.0), Vec3(0.0, 20.0, 0.0), 8.0))
        // A quarter of the 48-gon prism, 10 high.
        assertEquals(polygonCircleArea(8.0) / 4.0 * 10.0, solid.volume(), 1e-6)
    }

    @Test
    fun inverse_negatesTheVolume() {
        val solid = box(0.0, 0.0, 0.0, 2.0, 3.0, 4.0)
        assertEquals(-24.0, solid.inverse().volume(), 1e-9)
    }

    @Test
    fun wedgeCutter_leavesExactlyTheSlopeFrom25_40To60_15() {
        val solid = PartLibrary["wedge_cut"].build()
        val k = 1.0 / sqrt(25.0 * 25.0 + 35.0 * 35.0)
        val slope = solid.polygons.filter {
            abs(it.plane.normal.x - 25.0 * k) < 1e-9 && abs(it.plane.normal.y - 35.0 * k) < 1e-9
        }
        assertTrue(slope.isNotEmpty())
        val xs = slope.flatMap { it.vertices }.map { it.x }
        val ys = slope.flatMap { it.vertices }.map { it.y }
        assertEquals(25.0, xs.min(), 1e-9)
        assertEquals(60.0, xs.max(), 1e-9)
        assertEquals(15.0, ys.min(), 1e-9)
        assertEquals(40.0, ys.max(), 1e-9)
        assertEquals(78500.0, solid.volume(), 1e-6)
    }
}
