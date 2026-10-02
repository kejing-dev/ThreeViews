package io.embodit.threeviews.domain.geometry

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewProjectorTest {

    private fun drawing(id: String): PartDrawing = computeDrawing(id)

    private fun assertOnlyLines(lines: List<Polyline2>, vararg expected: DoubleArray) {
        val segments = lines.allSegments()
        assertEquals("number of straight segments", expected.size, segments.size)
        for (e in expected) {
            assertTrue(
                "missing line (${e[0]}, ${e[1]}) - (${e[2]}, ${e[3]}) in $segments",
                lines.hasSegment(e[0], e[1], e[2], e[3]),
            )
        }
    }

    private fun line(u0: Double, v0: Double, u1: Double, v1: Double) = doubleArrayOf(u0, v0, u1, v1)

    // ------------------------------------------------------------------------------------------
    // The simple blocks
    // ------------------------------------------------------------------------------------------

    @Test
    fun lBlock() {
        val d = drawing("l_block")

        assertEquals(210.0, d.front.visible.totalLength(), TOL)
        assertTrue(d.front.hidden.isEmpty())
        assertEquals(1, d.front.visible.size)
        assertTrue(d.front.visible[0].closed)
        assertEquals(6, d.front.visible[0].points.size)

        // 60 x 40 rectangle plus the step line at u = 15.
        assertEquals(240.0, d.top.visible.totalLength(), TOL)
        assertTrue(d.top.hidden.isEmpty())
        assertTrue(d.top.visible.hasSegment(15.0, -40.0, 15.0, 0.0))

        // 40 x 45 rectangle; the top of the base is behind the upright.
        assertEquals(170.0, d.left.visible.totalLength(), TOL)
        assertEquals(40.0, d.left.hidden.totalLength(), TOL)
        assertOnlyLines(d.left.hidden, line(0.0, 15.0, 40.0, 15.0))

        assertTrue(d.views.all { it.center.isEmpty() })
        assertEquals(Bounds2(0.0, 0.0, 60.0, 45.0), d.front.bounds)
        assertEquals(Bounds2(0.0, -40.0, 60.0, 0.0), d.top.bounds)
        assertEquals(Bounds2(0.0, 0.0, 40.0, 45.0), d.left.bounds)
    }

    @Test
    fun stepBlock() {
        val d = drawing("step_block")
        assertEquals(230.0, d.front.visible.totalLength(), TOL)
        assertTrue(d.front.hidden.isEmpty())

        assertEquals(220.0 + 2 * 40.0, d.top.visible.totalLength(), TOL)
        assertTrue(d.top.hidden.isEmpty())
        assertTrue(d.top.visible.hasSegment(20.0, -40.0, 20.0, 0.0))
        assertTrue(d.top.visible.hasSegment(45.0, -40.0, 45.0, 0.0))

        assertEquals(170.0, d.left.visible.totalLength(), TOL)
        assertOnlyLines(d.left.hidden, line(0.0, 15.0, 40.0, 15.0), line(0.0, 30.0, 40.0, 30.0))
    }

    @Test
    fun wedgeCut() {
        val d = drawing("wedge_cut")
        val slope = sqrt(35.0 * 35.0 + 25.0 * 25.0)
        assertEquals(60.0 + 15.0 + slope + 25.0 + 40.0, d.front.visible.totalLength(), TOL)
        assertEquals(183.012, d.front.visible.totalLength(), 1e-3)
        assertTrue(d.front.hidden.isEmpty())
        assertTrue(d.front.visible.hasSegment(25.0, 40.0, 60.0, 15.0))

        assertEquals(240.0, d.top.visible.totalLength(), TOL)
        assertTrue(d.top.hidden.isEmpty())
        assertTrue(d.top.visible.hasSegment(25.0, -40.0, 25.0, 0.0))

        assertEquals(160.0, d.left.visible.totalLength(), TOL)
        assertEquals(40.0, d.left.hidden.totalLength(), TOL)
        assertOnlyLines(d.left.hidden, line(0.0, 15.0, 40.0, 15.0))
    }

    @Test
    fun vBlock() {
        val d = drawing("v_block")
        val lip = 20.0 * 20.0 / 22.0          // half-width of the groove at the top face
        val flank = sqrt(lip * lip + 20.0 * 20.0)
        assertEquals(60.0 + 40.0 + 40.0 + 2 * (30.0 - lip) + 2 * flank, d.front.visible.totalLength(), TOL)
        assertTrue(d.front.hidden.isEmpty())
        assertTrue(d.front.visible.hasSegment(30.0 - lip, 40.0, 30.0, 20.0))
        assertTrue(d.front.visible.hasSegment(30.0, 20.0, 30.0 + lip, 40.0))

        assertEquals(200.0 + 3 * 40.0, d.top.visible.totalLength(), TOL)
        assertTrue(d.top.hidden.isEmpty())
        for (u in listOf(30.0 - lip, 30.0, 30.0 + lip)) {
            assertTrue(d.top.visible.hasSegment(u, -40.0, u, 0.0))
        }

        assertEquals(160.0, d.left.visible.totalLength(), TOL)
        assertOnlyLines(d.left.hidden, line(0.0, 20.0, 40.0, 20.0))
    }

    @Test
    fun slotBlock() {
        val d = drawing("slot_block")
        assertEquals(210.0, d.front.visible.totalLength(), TOL)
        assertTrue(d.front.hidden.isEmpty())
        assertEquals(1, d.front.visible.size)
        assertEquals(8, d.front.visible[0].points.size)

        assertEquals(200.0 + 2 * 40.0, d.top.visible.totalLength(), TOL)
        assertTrue(d.top.hidden.isEmpty())

        assertEquals(140.0, d.left.visible.totalLength(), TOL)
        assertOnlyLines(d.left.hidden, line(0.0, 15.0, 40.0, 15.0))
    }

    // ------------------------------------------------------------------------------------------
    // Parts with cylinders
    // ------------------------------------------------------------------------------------------

    @Test
    fun plateHole() {
        val d = drawing("plate_hole")

        // TOP: the rectangle and one closed circle.
        assertTrue(d.top.hidden.isEmpty())
        assertEquals(2, d.top.visible.size)
        val circles = d.top.visible.closedCircles(35.0, -22.0, 11.0)
        assertEquals(1, circles.size)
        val circle = circles[0]
        assertEquals(48, circle.points.size)
        assertEquals(2 * PI * 11.0, circle.length(), 2 * PI * 11.0 * 0.01)
        assertEquals(polygonCirclePerimeter(11.0), circle.length(), TOL)
        assertEquals(35.0, circle.points.sumOf { it.u } / circle.points.size, 1e-6)
        assertEquals(-22.0, circle.points.sumOf { it.v } / circle.points.size, 1e-6)
        val rectangle = d.top.visible.single { it !== circle }
        assertTrue(rectangle.closed)
        assertEquals(4, rectangle.points.size)
        assertEquals(228.0, rectangle.length(), TOL)

        // FRONT: outline, the two extreme generators of the hole as hidden lines, the axis.
        assertEquals(168.0, d.front.visible.totalLength(), TOL)
        assertOnlyLines(d.front.hidden, line(24.0, 0.0, 24.0, 14.0), line(46.0, 0.0, 46.0, 14.0))
        assertOnlyLines(d.front.center, line(35.0, -3.0, 35.0, 17.0))

        // TOP centre lines: a cross through the centre with arms of r + 3 = 14.
        assertOnlyLines(
            d.top.center,
            line(35.0 - 14.0, -22.0, 35.0 + 14.0, -22.0),
            line(35.0, -22.0 - 14.0, 35.0, -22.0 + 14.0),
        )

        // LEFT mirrors FRONT with the width axis.
        assertEquals(116.0, d.left.visible.totalLength(), TOL)
        assertOnlyLines(d.left.hidden, line(11.0, 0.0, 11.0, 14.0), line(33.0, 0.0, 33.0, 14.0))
        assertOnlyLines(d.left.center, line(22.0, -3.0, 22.0, 17.0))
    }

    @Test
    fun bearingSeat_front() {
        val v = drawing("bearing_seat").front

        // The bore: a closed circle of radius 10 about (40, 40).
        val bore = v.visible.closedCircles(40.0, 40.0, 10.0)
        assertEquals(1, bore.size)
        assertEquals(48, bore[0].points.size)
        assertEquals(polygonCirclePerimeter(10.0), bore[0].length(), TOL)

        // The boss: a half circle of radius 20 above v = 40, tangent to the sides of the upright.
        val arc = v.visible.allSegments().filter { (p, q) ->
            abs(p.distanceTo(Vec2(40.0, 40.0)) - 20.0) < 1e-6 &&
                abs(q.distanceTo(Vec2(40.0, 40.0)) - 20.0) < 1e-6 &&
                p.v >= 40.0 - 1e-6 && q.v >= 40.0 - 1e-6
        }
        assertEquals(24, arc.size)
        val arcLength = arc.sumOf { it.first.distanceTo(it.second) }
        assertEquals(PI * 20.0, arcLength, PI * 20.0 * 0.01)
        // Nothing of radius 20 below the centre, and no line across the tangent join at v = 40.
        assertTrue(v.visible.allSegments().none { (p, q) -> abs(p.v - 40.0) < 1e-6 && abs(q.v - 40.0) < 1e-6 })
        assertTrue(v.visible.hasSegment(20.0, 12.0, 20.0, 40.0))
        assertTrue(v.visible.hasSegment(60.0, 12.0, 60.0, 40.0))

        // Base 80 x 12, the upright's two sides, the arc and the bore.
        assertEquals(
            184.0 + 2 * 28.0 + polygonCirclePerimeter(20.0) / 2 + polygonCirclePerimeter(10.0),
            v.visible.totalLength(),
            TOL,
        )
        // The mounting holes are behind the front face of the base.
        assertOnlyLines(
            v.hidden,
            line(5.0, 0.0, 5.0, 12.0), line(15.0, 0.0, 15.0, 12.0),
            line(65.0, 0.0, 65.0, 12.0), line(75.0, 0.0, 75.0, 12.0),
        )
        assertOnlyLines(
            v.center,
            line(17.0, 40.0, 63.0, 40.0), line(40.0, 17.0, 40.0, 63.0),
            line(10.0, -3.0, 10.0, 15.0), line(70.0, -3.0, 70.0, 15.0),
        )
    }

    @Test
    fun bearingSeat_top() {
        val v = drawing("bearing_seat").top

        assertTrue("the bore must show as hidden lines", v.hidden.isNotEmpty())
        assertOnlyLines(v.hidden, line(30.0, -15.0, 30.0, 0.0), line(50.0, -15.0, 50.0, 0.0))

        for (cu in listOf(10.0, 70.0)) {
            val hole = v.visible.closedCircles(cu, -35.0, 5.0)
            assertEquals(1, hole.size)
            assertEquals(48, hole[0].points.size)
        }
        assertEquals(2, v.visible.count { it.closed && it.points.size == 48 })

        // Base 80 x 50, the upright's outline in front of the back edge, two holes.
        assertEquals(260.0 + 15.0 + 40.0 + 15.0 + 2 * polygonCirclePerimeter(5.0), v.visible.totalLength(), TOL)
        assertTrue(v.visible.hasSegment(20.0, -15.0, 60.0, -15.0))
        assertTrue(v.visible.hasSegment(20.0, 0.0, 20.0, -15.0))
        assertTrue(v.visible.hasSegment(60.0, 0.0, 60.0, -15.0))

        assertOnlyLines(
            v.center,
            line(40.0, -18.0, 40.0, 3.0),
            line(2.0, -35.0, 18.0, -35.0), line(10.0, -43.0, 10.0, -27.0),
            line(62.0, -35.0, 78.0, -35.0), line(70.0, -43.0, 70.0, -27.0),
        )
    }

    @Test
    fun bearingSeat_left() {
        val v = drawing("bearing_seat").left
        // Base 50 x 12 with its top edge, upright 15 wide rising to 60; no line at the tangent join.
        assertEquals(50.0 + 12.0 + 50.0 + 60.0 + 15.0 + 48.0, v.visible.totalLength(), TOL)
        assertTrue(v.visible.allSegments().none { (p, q) -> abs(p.v - 40.0) < 1e-6 && abs(q.v - 40.0) < 1e-6 })
        assertTrue(v.visible.hasSegment(0.0, 60.0, 15.0, 60.0))
        assertOnlyLines(
            v.hidden,
            line(0.0, 30.0, 15.0, 30.0), line(0.0, 50.0, 15.0, 50.0),   // bore
            line(30.0, 0.0, 30.0, 12.0), line(40.0, 0.0, 40.0, 12.0),   // both mounting holes coincide
        )
        assertOnlyLines(v.center, line(-3.0, 40.0, 18.0, 40.0), line(35.0, -3.0, 35.0, 15.0))
    }

    @Test
    fun notchedTube() {
        val d = drawing("notched_tube")
        // Where the notch walls (|z - 30| = 8) cut the outer and inner 48-gon prisms.
        val outer = d.top.visible.allSegments()
            .flatMap { listOf(it.first, it.second) }
            .filter { abs(it.v + 22.0) < 1e-6 }
            .minOf { it.u }
        val inner = 30.0 - sqrt(16.0 * 16.0 - 8.0 * 8.0)   // exactly a vertex of the inner prism (30 degrees)
        assertEquals(30.0 - sqrt(28.0 * 28.0 - 8.0 * 8.0), outer, 0.1)

        // TOP: both circles complete, plus the four edges of the notch floor; nothing hidden.
        assertTrue(d.top.hidden.isEmpty())
        assertEquals(1, d.top.visible.closedCircles(30.0, -30.0, 28.0).size)
        assertEquals(
            polygonCirclePerimeter(28.0) + polygonCirclePerimeter(16.0) + 4 * (inner - outer),
            d.top.visible.totalLength(),
            TOL,
        )
        for (vv in listOf(-22.0, -38.0)) {
            assertTrue(d.top.visible.hasSegment(outer, vv, inner, vv))
            assertTrue(d.top.visible.hasSegment(60.0 - inner, vv, 60.0 - outer, vv))
        }

        // FRONT: above the notch floor the outline steps in from the extreme generator to the cut edge.
        assertEquals(1, d.front.visible.size)
        assertTrue(d.front.visible[0].closed)
        assertEquals(56.0 + 2 * 35.0 + 2 * (outer - 2.0) + 2 * 15.0 + (60.0 - 2 * outer), d.front.visible.totalLength(), TOL)
        assertTrue(d.front.visible.hasSegment(2.0, 0.0, 2.0, 35.0))
        assertTrue(d.front.visible.hasSegment(2.0, 35.0, outer, 35.0))
        assertTrue(d.front.visible.hasSegment(outer, 35.0, outer, 50.0))
        assertOnlyLines(
            d.front.hidden,
            line(14.0, 0.0, 14.0, 35.0), line(46.0, 0.0, 46.0, 35.0),                   // bore, up to the notch
            line(inner, 35.0, inner, 50.0), line(60.0 - inner, 35.0, 60.0 - inner, 50.0), // notch wall meets bore
            line(outer, 35.0, inner, 35.0), line(60.0 - inner, 35.0, 60.0 - outer, 35.0), // notch floor
        )

        // LEFT: the notch is seen end-on as a visible rectangular cut; the bore is hidden full height.
        assertEquals(50.0 + 20.0 + 15.0 + 16.0 + 15.0 + 20.0 + 50.0 + 56.0, d.left.visible.totalLength(), TOL)
        assertTrue(d.left.visible.hasSegment(22.0, 35.0, 38.0, 35.0))
        assertTrue(d.left.visible.hasSegment(22.0, 35.0, 22.0, 50.0))
        assertTrue(d.left.visible.hasSegment(38.0, 35.0, 38.0, 50.0))
        assertOnlyLines(d.left.hidden, line(14.0, 0.0, 14.0, 50.0), line(46.0, 0.0, 46.0, 50.0))

        assertOnlyLines(d.front.center, line(30.0, -3.0, 30.0, 53.0))
        assertOnlyLines(d.left.center, line(30.0, -3.0, 30.0, 53.0))
        assertOnlyLines(d.top.center, line(-1.0, -30.0, 61.0, -30.0), line(30.0, -61.0, 30.0, 1.0))
    }

    // ------------------------------------------------------------------------------------------
    // Rules that hold for every part and view
    // ------------------------------------------------------------------------------------------

    @Test
    fun everyView_obeysTheLineRules() {
        for (part in PartLibrary.parts) {
            val d = drawing(part.id)
            for (view in d.views) {
                val name = "${part.id} ${view.view}"
                assertTrue("$name: at least one visible polyline", view.visible.isNotEmpty())

                val visible = view.visible.allSegments()
                val hidden = view.hidden.allSegments()
                for (i in visible.indices) for (j in i + 1 until visible.size) {
                    assertTrue("$name: visible segments overlap", overlapLength(visible[i], visible[j]) < 1e-6)
                }
                for (h in hidden) for (s in visible) {
                    assertTrue("$name: hidden overlaps visible", overlapLength(h, s) < 1e-6)
                }
                for (i in hidden.indices) for (j in i + 1 until hidden.size) {
                    assertTrue("$name: hidden segments overlap", overlapLength(hidden[i], hidden[j]) < 1e-6)
                }

                val b = view.bounds
                for (line in view.visible + view.hidden) {
                    assertTrue("$name: polyline too short", line.points.size >= 2)
                    assertTrue("$name: closed polyline needs three points", !line.closed || line.points.size >= 3)
                    for (p in line.points) {
                        assertTrue(
                            "$name: $p outside $b",
                            p.u >= b.minU - 1e-6 && p.u <= b.maxU + 1e-6 &&
                                p.v >= b.minV - 1e-6 && p.v <= b.maxV + 1e-6,
                        )
                    }
                    for ((p, q) in line.segments()) {
                        assertTrue("$name: degenerate segment", p.distanceTo(q) > 1e-4)
                    }
                }
                for (line in view.center) {
                    assertEquals(2, line.points.size)
                    assertTrue(!line.closed)
                }
                // The silhouette spans the whole bounding box in both directions.
                assertEquals(name, b.minU, visible.minOf { minOf(it.first.u, it.second.u) }, 1e-6)
                assertEquals(name, b.maxU, visible.maxOf { maxOf(it.first.u, it.second.u) }, 1e-6)
                assertEquals(name, b.minV, visible.minOf { minOf(it.first.v, it.second.v) }, 1e-6)
                assertEquals(name, b.maxV, visible.maxOf { maxOf(it.first.v, it.second.v) }, 1e-6)
            }
            // 长对正、高平齐、宽相等.
            assertEquals(d.front.bounds.minU, d.top.bounds.minU, 1e-9)
            assertEquals(d.front.bounds.maxU, d.top.bounds.maxU, 1e-9)
            assertEquals(d.front.bounds.minV, d.left.bounds.minV, 1e-9)
            assertEquals(d.front.bounds.maxV, d.left.bounds.maxV, 1e-9)
            assertEquals(d.top.bounds.height, d.left.bounds.width, 1e-9)
        }
    }

    @Test
    fun viewConventions_matchGbFirstAngle() {
        val p = Vec3(1.0, 2.0, 3.0)
        assertEquals(Vec2(1.0, 2.0), ViewKind.FRONT.project(p))
        assertEquals(Vec2(1.0, -3.0), ViewKind.TOP.project(p))      // front of the part at the bottom
        assertEquals(Vec2(3.0, 2.0), ViewKind.LEFT.project(p))      // front of the part on the right
        assertEquals(3.0, ViewKind.FRONT.depth(p), 0.0)
        assertEquals(2.0, ViewKind.TOP.depth(p), 0.0)
        assertEquals(-1.0, ViewKind.LEFT.depth(p), 0.0)
        assertEquals(Vec3(0.0, 0.0, 1.0), ViewKind.FRONT.towardViewer)
        assertEquals(Vec3(0.0, 1.0, 0.0), ViewKind.TOP.towardViewer)
        assertEquals(Vec3(-1.0, 0.0, 0.0), ViewKind.LEFT.towardViewer)
    }

    // ------------------------------------------------------------------------------------------
    // Cases beyond the library
    // ------------------------------------------------------------------------------------------

    @Test
    fun faceFragmentBorderingBothAFragmentAndACut_isSplitAtTheTJunction() {
        // Slot floor at y = 10: the BSP leaves the front face as three fragments, and the edge x = 20
        // of the left one borders a coplanar fragment for y < 10 and the slot above. Its midpoint
        // (y = 15) lies on the cut, so without splitting at the T-junction the whole edge would be drawn.
        val part = PartDefinition("test_slot", "", "", "", emptyList()) {
            box(0.0, 0.0, 0.0, 60.0, 30.0, 40.0).subtract(box(20.0, 10.0, -1.0, 40.0, 31.0, 41.0))
        }
        val d = computeDrawing(part)
        assertEquals(1, d.front.visible.size)
        assertEquals(8, d.front.visible[0].points.size)
        assertEquals(60.0 + 30.0 + 20.0 + 20.0 + 20.0 + 20.0 + 20.0 + 30.0, d.front.visible.totalLength(), TOL)
        assertTrue(d.front.hidden.isEmpty())
        assertEquals(24, d.mesh.sharpEdges.size)
    }

    @Test
    fun bossOnAPlate_showsItsExtremeGeneratorsAsVisibleSilhouette() {
        val part = PartDefinition(
            "test_boss", "", "", "",
            listOf(AxisFeature(Vec3(30.0, 10.0, 20.0), Vec3(30.0, 30.0, 20.0), 10.0)),
        ) {
            box(0.0, 0.0, 0.0, 60.0, 10.0, 40.0)
                .union(cylinder(Vec3(30.0, 10.0, 20.0), Vec3(30.0, 30.0, 20.0), 10.0))
        }
        val d = computeDrawing(part)

        assertEquals(140.0 + 3 * 20.0, d.front.visible.totalLength(), TOL)
        assertTrue(d.front.hidden.isEmpty())
        assertTrue(d.front.visible.hasSegment(20.0, 10.0, 20.0, 30.0))
        assertTrue(d.front.visible.hasSegment(40.0, 10.0, 40.0, 30.0))
        assertTrue(d.front.visible.hasSegment(20.0, 30.0, 40.0, 30.0))

        assertEquals(100.0 + 3 * 20.0, d.left.visible.totalLength(), TOL)
        assertTrue(d.left.hidden.isEmpty())

        assertEquals(200.0 + polygonCirclePerimeter(10.0), d.top.visible.totalLength(), TOL)
        assertEquals(1, d.top.visible.closedCircles(30.0, -20.0, 10.0).size)
        assertTrue(d.top.hidden.isEmpty())
        assertOnlyLines(d.front.center, line(30.0, 7.0, 30.0, 33.0))
    }

    @Test
    fun blindPocket_isHiddenFromTheSideAndVisibleFromAbove() {
        // A pocket that does not go through: its floor and walls are hidden in the front view.
        val part = PartDefinition("test_pocket", "", "", "", emptyList()) {
            box(0.0, 0.0, 0.0, 60.0, 30.0, 40.0).subtract(box(20.0, 10.0, 10.0, 40.0, 31.0, 30.0))
        }
        val d = computeDrawing(part)
        assertEquals(180.0, d.front.visible.totalLength(), TOL)
        assertOnlyLines(
            d.front.hidden,
            line(20.0, 10.0, 20.0, 30.0), line(40.0, 10.0, 40.0, 30.0), line(20.0, 10.0, 40.0, 10.0),
        )
        // From above: the outline and the pocket's rim; the floor edges lie under the rim.
        assertEquals(200.0 + 80.0, d.top.visible.totalLength(), TOL)
        assertTrue(d.top.hidden.isEmpty())
        assertEquals(72000.0 - 20.0 * 20 * 20, d.mesh.volume(), 1e-3)
    }

    // ------------------------------------------------------------------------------------------
    // Facade
    // ------------------------------------------------------------------------------------------

    @Test
    fun projectByPart_equalsTheFacade() {
        for (part in PartLibrary.parts) {
            val d = computeDrawing(part.id)
            for (kind in ViewKind.entries) {
                assertEquals(d.view(kind), ViewProjector.project(part, kind))
                assertEquals(kind, d.view(kind).view)
            }
            assertTrue(d.part === part)
        }
    }

    @Test
    fun computeDrawing_isDeterministicAndThreadSafe() {
        val reference = PartLibrary.parts.associate { it.id to computeDrawing(it.id) }
        val pool = Executors.newFixedThreadPool(4)
        try {
            val tasks = (0 until 4).flatMap { PartLibrary.parts.map { it.id } }.map { id ->
                Callable { id to computeDrawing(id) }
            }
            for (future in pool.invokeAll(tasks)) {
                val (id, d) = future.get()
                val r = reference.getValue(id)
                assertEquals(r.front, d.front)
                assertEquals(r.top, d.top)
                assertEquals(r.left, d.left)
                assertTrue(r.mesh.positions.contentEquals(d.mesh.positions))
                assertTrue(r.mesh.normals.contentEquals(d.mesh.normals))
                assertTrue(r.mesh.indices.contentEquals(d.mesh.indices))
                assertEquals(r.mesh.sharpEdges, d.mesh.sharpEdges)
            }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun allPartsAndViews_computeInUnderThreeSeconds() {
        val start = System.nanoTime()
        var polylines = 0
        for (part in PartLibrary.parts) {
            val d = computeDrawing(part.id)
            polylines += d.views.sumOf { it.visible.size + it.hidden.size + it.center.size }
        }
        val millis = (System.nanoTime() - start) / 1e6
        println("ThreeViews: 8 parts x 3 views computed in %.1f ms (%d polylines)".format(millis, polylines))
        assertTrue(polylines > 0)
        assertTrue("took $millis ms", millis < 3000.0)
    }
}
