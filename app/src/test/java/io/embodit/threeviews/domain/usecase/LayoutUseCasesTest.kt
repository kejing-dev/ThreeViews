package io.embodit.threeviews.domain.usecase

import io.embodit.threeviews.data.repository.FakePartRepository
import io.embodit.threeviews.domain.geometry.Polyline2
import io.embodit.threeviews.domain.geometry.Vec2
import io.embodit.threeviews.domain.geometry.Vec3
import io.embodit.threeviews.domain.geometry.ViewKind
import io.embodit.threeviews.domain.geometry.computeDrawing
import io.embodit.threeviews.domain.model.AlignmentRule
import io.embodit.threeviews.domain.model.DrawingStatus
import io.embodit.threeviews.domain.model.SheetPoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetLayoutUseCaseTest {
    private val useCase = SheetLayoutUseCase()
    private val lBlock = computeDrawing("l_block") // 60 x 45 x 40

    @Test
    fun `front view is top-left, top view below it, left view to its right`() {
        val sheet = useCase.layout(lBlock, gap = 28.0)
        assertEquals(0.0, sheet.front.x, 1e-9); assertEquals(0.0, sheet.front.y, 1e-9)
        assertEquals(60.0, sheet.front.width, 1e-9); assertEquals(45.0, sheet.front.height, 1e-9)
        assertEquals(0.0, sheet.top.x, 1e-9); assertEquals(45.0 + 28.0, sheet.top.y, 1e-9)
        assertEquals(60.0 + 28.0, sheet.left.x, 1e-9); assertEquals(0.0, sheet.left.y, 1e-9)
        assertEquals(60.0 + 28.0 + 40.0, sheet.width, 1e-9)
        assertEquals(45.0 + 28.0 + 40.0, sheet.height, 1e-9)
    }

    @Test
    fun `length aligns, height is level, width is equal`() {
        val sheet = useCase.layout(lBlock)
        assertEquals(sheet.front.width, sheet.top.width, 1e-9)   // 长对正
        assertEquals(sheet.front.x, sheet.top.x, 1e-9)
        assertEquals(sheet.front.height, sheet.left.height, 1e-9) // 高平齐
        assertEquals(sheet.front.y, sheet.left.y, 1e-9)
        assertEquals(sheet.top.height, sheet.left.width, 1e-9)    // 宽相等
    }

    @Test
    fun `a drawing point maps onto the sheet with y running down`() {
        val sheet = useCase.layout(lBlock)
        // top of the upright in the front view
        assertEquals(SheetPoint(0.0, 0.0), sheet.front.toSheet(Vec2(0.0, 45.0)))
        assertEquals(SheetPoint(60.0, 45.0), sheet.front.toSheet(Vec2(60.0, 0.0)))
        // the front edge of the part (z = 40) is the bottom edge of the top view
        assertEquals(sheet.top.bottom, sheet.top.toSheet(Vec2(0.0, -40.0)).y, 1e-9)
    }

    @Test
    fun `the 45 degree line carries widths from the top view into the left view`() {
        val sheet = useCase.layout(lBlock)
        assertEquals(sheet.miterEnd.x - sheet.miterStart.x, sheet.miterEnd.y - sheet.miterStart.y, 1e-9)
        val widthGuides = sheet.guides.filter { it.rule == AlignmentRule.WIDTH }
        assertEquals(2, widthGuides.size)
        for (g in widthGuides) {
            val corner = g.points[1]
            // the turning point lies on the miter line
            assertEquals(corner.x - sheet.miterStart.x, corner.y - sheet.miterStart.y, 1e-9)
            // it leaves the top view horizontally and enters the left view vertically
            assertEquals(g.points[0].y, corner.y, 1e-9)
            assertEquals(g.points[2].x, corner.x, 1e-9)
        }
        // the upright's right face (x = 15) and the base's top face (y = 15) get a guide of their own
        assertEquals(3, sheet.guides.count { it.rule == AlignmentRule.LENGTH })
        assertEquals(3, sheet.guides.count { it.rule == AlignmentRule.HEIGHT })
    }

    @Test
    fun `the axes cross half a gap outside the front view and all three rules hold`() {
        val sheet = useCase.layout(lBlock, gap = 28.0)
        assertEquals(SheetPoint(74.0, 59.0), sheet.origin)
        assertEquals(sheet.origin, sheet.miterStart)
        assertEquals(-14.0, sheet.paperLeft, 1e-9); assertEquals(-14.0, sheet.paperTop, 1e-9)
        assertEquals(sheet.width + 14.0, sheet.paperRight, 1e-9); assertEquals(sheet.height + 14.0, sheet.paperBottom, 1e-9)
        assertEquals(AlignmentRule.entries.associateWith { true }, sheet.holds)
    }

    @Test
    fun `the edges of a hole get guides, the facets of its circle do not`() {
        val sheet = useCase.layout(computeDrawing("plate_hole"))
        // the two ends of the plate plus the two generators of the hole
        assertEquals(4, sheet.guides.count { it.rule == AlignmentRule.LENGTH })
        assertEquals(4, sheet.guides.count { it.rule == AlignmentRule.WIDTH })
        assertEquals(2, sheet.guides.count { it.rule == AlignmentRule.HEIGHT })
        assertTrue(sheet.guides.count { it.rule == AlignmentRule.LENGTH } <= SheetLayoutUseCase.MAX_GUIDES)
    }
}

class BoxLayoutUseCaseTest {
    private val useCase = BoxLayoutUseCase()
    private val lBlock = computeDrawing("l_block")

    @Test
    fun `planes sit one margin behind, below and to the right of the part`() {
        val box = useCase.layout(lBlock.mesh, margin = 14.0)
        assertEquals(-14.0, box.backZ, 1e-9)
        assertEquals(-14.0, box.bottomY, 1e-9)
        assertEquals(74.0, box.rightX, 1e-9)
        assertEquals(88.0, box.lengthX, 1e-9)
        assertEquals(73.0, box.heightY, 1e-9)
        assertEquals(68.0, box.depthZ, 1e-9)
    }

    @Test
    fun `images land on the right plane`() {
        val box = useCase.layout(lBlock.mesh)
        val p = Vec3(15.0, 45.0, 40.0)
        assertEquals(Vec3(15.0, 45.0, box.backZ), useCase.imageOf(p, ViewKind.FRONT, box))
        assertEquals(Vec3(15.0, box.bottomY, 40.0), useCase.imageOf(p, ViewKind.TOP, box))
        assertEquals(Vec3(box.rightX, 45.0, 40.0), useCase.imageOf(p, ViewKind.LEFT, box))
    }

    @Test
    fun `an L block has twelve corners and one ray per distinct image point`() {
        assertEquals(12, useCase.corners(lBlock.mesh.sharpEdges).size)
        val box = useCase.layout(lBlock.mesh)
        val front = useCase.rays(lBlock.mesh, ViewKind.FRONT, box)
        assertEquals(6, front.size) // the L outline has six vertices
        // every ray starts at the corner nearest the viewer and runs parallel to the projection direction
        for (ray in front) {
            assertEquals(40.0, ray.from.z, 1e-9)
            assertEquals(box.backZ, ray.to.z, 1e-9)
            assertEquals(ray.from.x, ray.to.x, 1e-9); assertEquals(ray.from.y, ray.to.y, 1e-9)
        }
        assertEquals(6, useCase.rays(lBlock.mesh, ViewKind.TOP, box).size)
        assertEquals(6, useCase.rays(lBlock.mesh, ViewKind.LEFT, box).size)
    }

    @Test
    fun `a round hole does not sprout rays along its rim`() {
        val plate = computeDrawing("plate_hole")
        assertEquals(8, useCase.corners(plate.mesh.sharpEdges).size)
    }
}

class LineDashUseCaseTest {
    private val useCase = LineDashUseCase()

    @Test
    fun `a dashed line alternates drawn and skipped lengths`() {
        val line = Polyline2(listOf(Vec2(0.0, 0.0), Vec2(10.0, 0.0)), closed = false)
        val strokes = useCase.dashed(line, doubleArrayOf(4.0, 1.0))
        assertEquals(2, strokes.size)
        assertEquals(0.0, strokes[0].a.u, 1e-9); assertEquals(4.0, strokes[0].b.u, 1e-9)
        assertEquals(5.0, strokes[1].a.u, 1e-9); assertEquals(9.0, strokes[1].b.u, 1e-9)
    }

    @Test
    fun `the pattern keeps running around a corner`() {
        val line = Polyline2(listOf(Vec2(0.0, 0.0), Vec2(3.0, 0.0), Vec2(3.0, 3.0)), closed = false)
        val strokes = useCase.dashed(line, doubleArrayOf(4.0, 1.0))
        // 3 mm on the first leg + 1 mm on the second complete the first dash; after the gap 1 mm is left
        assertEquals(3, strokes.size)
        assertEquals(4.0, strokes[0].length + strokes[1].length, 1e-9)
        assertEquals(1.0, strokes[2].length, 1e-9)
        assertEquals(2.0, strokes[2].a.v, 1e-9)
    }

    @Test
    fun `a closed polyline is dashed all the way round and solid lines keep every segment`() {
        val square = Polyline2(listOf(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(10.0, 10.0), Vec2(0.0, 10.0)), closed = true)
        assertEquals(4, useCase.solid(square).size)
        val drawn = useCase.dashed(square, LineDashUseCase.HIDDEN).sumOf { it.length }
        assertEquals(32.0, drawn, 1e-9) // 40 mm perimeter, 4 on / 1 off
    }
}

class PrepareDrawingUseCaseTest {
    @Test
    fun `a part becomes a drawing with matching sheet and box layouts`() = runBlocking {
        val repository = FakePartRepository()
        val status = PrepareDrawingUseCase(repository).prepare("l_block")
        assertTrue(status is DrawingStatus.Ready)
        status as DrawingStatus.Ready
        assertEquals(status.box.margin * 2, status.sheet.gap, 1e-9)
        // the unfolded box and the sheet are the same picture
        assertEquals(status.box.lengthX + status.box.depthZ - 2 * status.box.margin, status.sheet.width, 1e-9)
        assertEquals(status.box.heightY + status.box.depthZ - 2 * status.box.margin, status.sheet.height, 1e-9)
    }

    @Test
    fun `a failing or unknown part is reported, not thrown`() = runBlocking {
        val repository = FakePartRepository(failingIds = setOf("plate_hole"))
        val useCase = PrepareDrawingUseCase(repository)
        assertEquals(DrawingStatus.Failed("plate_hole"), useCase.prepare("plate_hole"))
        assertEquals(DrawingStatus.Failed("no_such_part"), useCase.prepare("no_such_part"))
    }
}
