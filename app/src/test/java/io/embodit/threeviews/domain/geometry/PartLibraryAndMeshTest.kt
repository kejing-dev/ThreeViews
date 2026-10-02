package io.embodit.threeviews.domain.geometry

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PartLibraryAndMeshTest {

    /** Analytic volumes in cubic millimetres; circles are the 48-gons the cylinders are built from. */
    private val expectedVolumes: Map<String, Double> = run {
        val a = ::polygonCircleArea
        // Area of a circle of radius r inside the strip |z| < h (used only for the tube's notch).
        fun strip(r: Double, h: Double) = 2.0 * (h * sqrt(r * r - h * h) + r * r * asin(h / r))
        mapOf(
            "l_block" to 60.0 * 15 * 40 + 15.0 * 30 * 40,
            "step_block" to 70.0 * 15 * 40 + 45.0 * 15 * 40 + 20.0 * 15 * 40,
            "wedge_cut" to 60.0 * 40 * 40 - 0.5 * 35 * 25 * 40,
            "plate_hole" to 70.0 * 14 * 44 - a(11.0, 48) * 14,
            // V groove clipped by the top face: apex (30,20), 20 deep, 2 * 20 * 20 / 22 wide at the top.
            "v_block" to 60.0 * 40 * 40 - 0.5 * (2 * 20.0 * 20 / 22) * 20 * 40,
            "slot_block" to 60.0 * 30 * 40 - 20.0 * 15 * 40,
            "bearing_seat" to 80.0 * 12 * 50 - 2 * a(5.0, 48) * 12 +
                40.0 * 28 * 15 + a(20.0, 48) / 2 * 15 - a(10.0, 48) * 15,
            "notched_tube" to (a(28.0, 48) - a(16.0, 48)) * 50 - (strip(28.0, 8.0) - strip(16.0, 8.0)) * 15,
        )
    }

    @Test
    fun library_hasExactlyTheEightParts() {
        assertEquals(
            listOf(
                "l_block", "step_block", "wedge_cut", "plate_hole",
                "v_block", "slot_block", "bearing_seat", "notched_tube",
            ),
            PartLibrary.parts.map { it.id },
        )
        assertEquals(
            listOf("L 形块", "阶梯块", "斜切块", "带孔底板", "V 形块", "开槽块", "支座", "开口圆筒"),
            PartLibrary.parts.map { it.nameZh },
        )
        for (part in PartLibrary.parts) {
            assertTrue(part.nameEn.isNotBlank())
            assertTrue(part.descriptionZh.isNotBlank())
            assertTrue("one line", '\n' !in part.descriptionZh)
            assertTrue(PartLibrary[part.id] === part)
            assertTrue(PartLibrary.find(part.id) === part)
        }
        assertNull(PartLibrary.find("nope"))
        try {
            PartLibrary["nope"]
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // ok
        }
        try {
            computeDrawing("nope")
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun library_boundingBoxes() {
        fun assertBounds(lo: Vec3, hi: Vec3, id: String) {
            // Vertices created by a cut carry rounding noise of a few ulp, hence the tolerance.
            val (actualLo, actualHi) = PartLibrary[id].build().bounds()
            assertTrue("$id min $actualLo", actualLo.distanceTo(lo) < 1e-9)
            assertTrue("$id max $actualHi", actualHi.distanceTo(hi) < 1e-9)
        }
        assertBounds(Vec3(0.0, 0.0, 0.0), Vec3(60.0, 45.0, 40.0), "l_block")
        assertBounds(Vec3(0.0, 0.0, 0.0), Vec3(70.0, 45.0, 40.0), "step_block")
        assertBounds(Vec3(0.0, 0.0, 0.0), Vec3(60.0, 40.0, 40.0), "wedge_cut")
        assertBounds(Vec3(0.0, 0.0, 0.0), Vec3(70.0, 14.0, 44.0), "plate_hole")
        assertBounds(Vec3(0.0, 0.0, 0.0), Vec3(60.0, 40.0, 40.0), "v_block")
        assertBounds(Vec3(0.0, 0.0, 0.0), Vec3(60.0, 30.0, 40.0), "slot_block")
        assertBounds(Vec3(0.0, 0.0, 0.0), Vec3(80.0, 60.0, 50.0), "bearing_seat")
        assertBounds(Vec3(2.0, 0.0, 2.0), Vec3(58.0, 50.0, 58.0), "notched_tube")
    }

    @Test
    fun library_axes() {
        fun axes(id: String) = PartLibrary[id].axes
        for (id in listOf("l_block", "step_block", "wedge_cut", "v_block", "slot_block")) {
            assertTrue(axes(id).isEmpty())
        }
        assertEquals(listOf(AxisFeature(Vec3(35.0, 0.0, 22.0), Vec3(35.0, 14.0, 22.0), 11.0)), axes("plate_hole"))
        assertEquals(
            listOf(
                // Boss and bore are concentric: one entry, with the larger radius.
                AxisFeature(Vec3(40.0, 40.0, 0.0), Vec3(40.0, 40.0, 15.0), 20.0),
                AxisFeature(Vec3(10.0, 0.0, 35.0), Vec3(10.0, 12.0, 35.0), 5.0),
                AxisFeature(Vec3(70.0, 0.0, 35.0), Vec3(70.0, 12.0, 35.0), 5.0),
            ),
            axes("bearing_seat"),
        )
        assertEquals(listOf(AxisFeature(Vec3(30.0, 0.0, 30.0), Vec3(30.0, 50.0, 30.0), 28.0)), axes("notched_tube"))
        // Axes are clipped to the real feature, not to the oversized cutters.
        for (part in PartLibrary.parts) {
            val (lo, hi) = part.build().bounds()
            for (axis in part.axes) for (p in listOf(axis.start, axis.end)) {
                assertTrue(p.x in lo.x..hi.x && p.y in lo.y..hi.y && p.z in lo.z..hi.z)
            }
        }
    }

    @Test
    fun mesh_volumeMatchesTheAnalyticVolume() {
        // The four volumes required by the brief, within 1 %.
        for (id in listOf("l_block", "step_block", "slot_block", "plate_hole")) {
            val expected = expectedVolumes.getValue(id)
            assertEquals(id, expected, computeDrawing(id).mesh.volume(), expected * 0.01)
        }
        assertEquals(54000.0, expectedVolumes.getValue("l_block"), 0.0)
        // In fact every part is far closer than that (the tube uses true circles for its notch, hence 0.1 %).
        for (part in PartLibrary.parts) {
            val expected = expectedVolumes.getValue(part.id)
            val relative = if (part.id == "notched_tube") 1e-3 else 1e-6
            assertEquals(part.id, expected, computeDrawing(part.id).mesh.volume(), expected * relative)
            assertEquals(part.id, expected, part.build().volume(), expected * relative)
        }
    }

    @Test
    fun mesh_isWellFormed() {
        for (part in PartLibrary.parts) {
            val mesh = computeDrawing(part.id).mesh
            assertEquals(mesh.positions.size, mesh.normals.size)
            assertEquals(0, mesh.positions.size % 3)
            assertEquals(0, mesh.indices.size % 3)
            assertTrue(mesh.triangleCount > 0)
            assertTrue(mesh.indices.all { it in 0 until mesh.vertexCount })

            fun position(i: Int) = Vec3(
                mesh.positions[i * 3].toDouble(),
                mesh.positions[i * 3 + 1].toDouble(),
                mesh.positions[i * 3 + 2].toDouble(),
            )
            fun normal(i: Int) = Vec3(
                mesh.normals[i * 3].toDouble(),
                mesh.normals[i * 3 + 1].toDouble(),
                mesh.normals[i * 3 + 2].toDouble(),
            )

            var areaVector = Vec3.ZERO
            for (t in 0 until mesh.triangleCount) {
                val ia = mesh.indices[t * 3]
                val ib = mesh.indices[t * 3 + 1]
                val ic = mesh.indices[t * 3 + 2]
                val cross = (position(ib) - position(ia)).cross(position(ic) - position(ia))
                areaVector += cross
                for (i in listOf(ia, ib, ic)) {
                    assertEquals("${part.id}: unit normal", 1.0, normal(i).length(), 1e-5)
                    // Flat shading: all three vertices carry the face normal.
                    assertTrue(normal(i).distanceTo(normal(ia)) < 1e-6)
                }
                // Counter-clockwise seen from outside (slivers are too small to judge in float precision).
                if (cross.length() > 1e-2) {
                    assertTrue("${part.id}: winding", cross.unit().dot(normal(ia)) > 0.999)
                }
            }
            // A closed surface has a zero vector area (positions are floats, hence the loose bound;
            // the smallest face of any part is far larger than this).
            assertTrue("${part.id}: closed", areaVector.length() < 0.5)

            val (lo, hi) = part.build().bounds()
            assertTrue(mesh.boundsMin.distanceTo(lo) < 1e-9)
            assertTrue(mesh.boundsMax.distanceTo(hi) < 1e-9)
        }
    }

    @Test
    fun mesh_isWatertight() {
        // Every directed triangle edge must be matched by the opposite edge of a neighbouring triangle.
        for (part in PartLibrary.parts) {
            val mesh = computeDrawing(part.id).mesh
            fun key(i: Int): Triple<Int, Int, Int> = Triple(
                Math.round(mesh.positions[i * 3] * 1000f),
                Math.round(mesh.positions[i * 3 + 1] * 1000f),
                Math.round(mesh.positions[i * 3 + 2] * 1000f),
            )
            val edges = HashMap<Pair<Triple<Int, Int, Int>, Triple<Int, Int, Int>>, Int>()
            for (t in 0 until mesh.triangleCount) {
                for (k in 0..2) {
                    val a = key(mesh.indices[t * 3 + k])
                    val b = key(mesh.indices[t * 3 + (k + 1) % 3])
                    if (a == b) continue
                    edges.merge(a to b, 1, Int::plus)
                }
            }
            for ((edge, count) in edges) {
                val opposite = edges[edge.second to edge.first] ?: 0
                assertEquals("${part.id}: unmatched edge $edge", count, opposite)
            }
        }
    }

    @Test
    fun mesh_sharpEdgesAreTheMergedFeatureEdges() {
        // A prism over an n-gon has 3n edges: the two profiles and n lateral edges.
        val l = computeDrawing("l_block").mesh.sharpEdges
        assertEquals(18, l.size)
        assertEquals(2 * 210.0 + 6 * 40.0, l.sumOf { it.length() }, TOL)

        val step = computeDrawing("step_block").mesh.sharpEdges
        assertEquals(24, step.size)
        assertEquals(2 * 230.0 + 8 * 40.0, step.sumOf { it.length() }, TOL)

        val slot = computeDrawing("slot_block").mesh.sharpEdges
        assertEquals(24, slot.size)
        assertEquals(2 * 210.0 + 8 * 40.0, slot.sumOf { it.length() }, TOL)

        val wedge = computeDrawing("wedge_cut").mesh.sharpEdges
        assertEquals(15, wedge.size)

        // Plate with hole: 12 box edges and two rims of 48 chords; the hole's facet joins are smooth.
        val plate = computeDrawing("plate_hole").mesh.sharpEdges
        assertEquals(12 + 2 * 48, plate.size)
        assertEquals(
            4 * (70.0 + 14 + 44) + 2 * polygonCirclePerimeter(11.0),
            plate.sumOf { it.length() },
            TOL,
        )

        // No two sharp edges of any part lie on top of each other.
        for (part in PartLibrary.parts) {
            val edges = computeDrawing(part.id).mesh.sharpEdges
            for (i in edges.indices) for (j in i + 1 until edges.size) {
                val a = edges[i]
                val b = edges[j]
                val d = (a.b - a.a).unit()
                if ((b.a - a.a).cross(d).length() > 1e-6 || (b.b - a.a).cross(d).length() > 1e-6) continue
                val t0 = (b.a - a.a).dot(d)
                val t1 = (b.b - a.a).dot(d)
                val overlap = minOf(a.length(), maxOf(t0, t1)) - maxOf(0.0, minOf(t0, t1))
                assertTrue("${part.id}: sharp edges $i and $j overlap", overlap < 1e-6)
            }
            assertTrue(edges.all { it.length() > 1e-3 })
            assertTrue(abs(edges.sumOf { it.length() }) > 0)
        }
    }
}
