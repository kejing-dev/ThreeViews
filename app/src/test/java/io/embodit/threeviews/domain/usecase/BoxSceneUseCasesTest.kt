package io.embodit.threeviews.domain.usecase

import io.embodit.threeviews.data.repository.FakePartRepository
import io.embodit.threeviews.domain.geometry.Segment3
import io.embodit.threeviews.domain.geometry.Vec3
import io.embodit.threeviews.domain.geometry.ViewKind
import io.embodit.threeviews.domain.model.DrawingStatus
import io.embodit.threeviews.domain.model.SceneState
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun ready(partId: String): DrawingStatus.Ready = runBlocking {
    PrepareDrawingUseCase(FakePartRepository()).prepare(partId) as DrawingStatus.Ready
}

class BoxSceneUseCaseTest {
    private val useCase = BoxSceneUseCase()
    private val lBlock = ready("l_block")

    @Test
    fun `a drawing point lands where its projection ray ends`() {
        val box = lBlock.box
        val p = Vec3(15.0, 45.0, 40.0)
        val layout = BoxLayoutUseCase()
        for (view in ViewKind.entries) {
            assertEquals(layout.imageOf(p, view, box), useCase.onPlane(view.project(p), view, box))
        }
    }

    @Test
    fun `the front view of an L block is six thick strokes floating just off plane V`() {
        val spec = useCase.build(lBlock)
        val front = spec.plane(ViewKind.FRONT)
        assertEquals(6, front.visible.quadCount)
        assertTrue(front.hidden.isEmpty)
        assertTrue(front.center.isEmpty)
        val z = (spec.box.backZ + BoxSceneUseCase.LINE_LIFT).toFloat()
        for (i in 2 until front.visible.positions.size step 3) assertEquals(z, front.visible.positions[i], 1e-4f)
        // square caps let the outline reach half a line width past the part on every side
        assertEquals(-BoxSceneUseCase.VISIBLE_WIDTH / 2, front.visible.boundsMin.x, 1e-6)
        assertEquals(60.0 + BoxSceneUseCase.VISIBLE_WIDTH / 2, front.visible.boundsMax.x, 1e-6)
        assertEquals(4, front.border.quadCount)
    }

    @Test
    fun `a hole gives dashed hidden lines and a chain centre line`() {
        val spec = useCase.build(ready("plate_hole"))
        val front = spec.plane(ViewKind.FRONT)
        assertFalse(front.hidden.isEmpty)
        assertFalse(front.center.isEmpty)
        // two hidden generators of the hole, each cut into several dashes
        assertTrue(front.hidden.quadCount > 2)
        // in the top view the hole is a visible circle and has two crossing centre lines
        val top = spec.plane(ViewKind.TOP)
        assertTrue(top.visible.quadCount > 4)
        assertFalse(top.center.isEmpty)
        val y = (spec.box.bottomY + BoxSceneUseCase.LINE_LIFT).toFloat()
        for (i in 1 until top.visible.positions.size step 3) assertEquals(y, top.visible.positions[i], 1e-4f)
    }

    @Test
    fun `rays and edges are crossed ribbons, two quads per line`() {
        val spec = useCase.build(lBlock)
        for (view in ViewKind.entries) assertEquals(12, spec.rays.getValue(view).quadCount) // six rays each
        assertEquals(18 * 2, spec.edges.quadCount) // an L prism has 18 edges
        val one = useCase.crossRibbons(listOf(Segment3(Vec3(0.0, 0.0, 0.0), Vec3(0.0, 0.0, 10.0))), width = 2.0)
        assertEquals(2, one.quadCount)
        assertEquals(Vec3(-1.0, -1.0, 0.0), one.boundsMin)
        assertEquals(Vec3(1.0, 1.0, 10.0), one.boundsMax)
        assertTrue(useCase.crossRibbons(emptyList(), 1.0).isEmpty)
    }
}

class BoxPoseUseCaseTest {
    private val useCase = BoxPoseUseCase()
    private val lBlock = ready("l_block")
    private val box = lBlock.box
    private val lo = lBlock.drawing.mesh.boundsMin
    private val hi = lBlock.drawing.mesh.boundsMax

    @Test
    fun `folded, the box turns about its centre and the part stays in the corner`() {
        val pose = useCase.pose(box, lo, hi, unfold = 0.0, yawDeg = 28.0)
        assertEquals(box.center, pose.pivot)
        assertEquals(28.0, pose.yawDeg, 1e-9)
        assertEquals(BoxPoseUseCase.TILT_DEG, pose.tiltDeg, 1e-9)
        assertEquals(0.0, pose.hingeDeg, 1e-9)
        assertEquals((lo + hi) * 0.5, pose.partPosition)
        assertEquals(1.0, pose.partScale, 1e-9)
    }

    @Test
    fun `unfolded, the three planes are one flat sheet facing the viewer`() {
        val pose = useCase.pose(box, lo, hi, unfold = 1.0, yawDeg = -60.0)
        assertEquals(0.0, pose.yawDeg, 1e-9)
        assertEquals(0.0, pose.tiltDeg, 1e-9)
        assertEquals(90.0, pose.hingeDeg, 1e-9)
        val planes = useCase.extremePoints(box, lo, hi, pose).take(12)
        for (p in planes) assertEquals(planes[0].z, p.z, 1e-9)
        val width = planes.maxOf { it.x } - planes.minOf { it.x }
        val height = planes.maxOf { it.y } - planes.minOf { it.y }
        assertEquals(box.lengthX + box.depthZ, width, 1e-9)
        assertEquals(box.heightY + box.depthZ, height, 1e-9)
        // the sheet is centred in the volume
        assertEquals(0.0, planes.maxOf { it.x } + planes.minOf { it.x }, 1e-9)
        assertEquals(0.0, planes.maxOf { it.y } + planes.minOf { it.y }, 1e-9)
        // and it is the sheet of the 2D window: views plus the gap between them
        assertEquals(lBlock.sheet.width + 2 * box.margin, width, 1e-9)
        assertEquals(lBlock.sheet.height + 2 * box.margin, height, 1e-9)
    }

    @Test
    fun `the pictorial view sits in the empty fourth quadrant, in front of the sheet`() {
        val pict = useCase.pictorial(box, lo, hi)
        val reach = pict.radius * pict.scale
        assertTrue(pict.scale <= 1.0)
        assertTrue(pict.center.x - reach >= box.rightX)
        assertTrue(pict.center.x + reach <= box.rightX + box.depthZ)
        assertTrue(pict.center.y + reach <= box.bottomY)
        assertTrue(pict.center.y - reach >= box.bottomY - box.depthZ)
        assertTrue(pict.center.z - reach > box.backZ)
        val pose = useCase.pose(box, lo, hi, unfold = 1.0, yawDeg = 0.0)
        assertEquals(pict.center, pose.partPosition)
        assertEquals(pict.scale, pose.partScale, 1e-9)
    }

    @Test
    fun `the fitted box stays inside the volume for every yaw and every moment of the unfold`() {
        val region = Vec3(0.35, 0.35, 0.30)
        val fit = useCase.fit(box, lo, hi, region)
        fun worstAt(e: Double): Vec3 {
            var worst = Vec3.ZERO
            var yaw = SceneState.MIN_YAW.toDouble()
            while (yaw <= SceneState.MAX_YAW) {
                for (p in useCase.extremePoints(box, lo, hi, useCase.pose(box, lo, hi, e, yaw))) {
                    val m = p * fit.metresPerMm(e)
                    worst = Vec3(maxOf(worst.x, abs(m.x)), maxOf(worst.y, abs(m.y)), maxOf(worst.z, abs(m.z)))
                }
                yaw += 2.5
            }
            return worst
        }
        // finer than the samples the fit itself uses, so the interpolation between them is checked too
        for (step in 0..80) {
            val worst = worstAt(step / 80.0)
            assertTrue("x ${worst.x} at $step", worst.x <= region.x)
            assertTrue("y ${worst.y} at $step", worst.y <= region.y)
            assertTrue("z ${worst.z} at $step", worst.z <= region.z)
        }
        // folded, the limiting axis uses the volume up to the safety share
        val folded = worstAt(0.0)
        val used = when (fit.limitingAxis) { 'X' -> folded.x / region.x; 'Y' -> folded.y / region.y; else -> folded.z / region.z }
        assertTrue("used $used", used > BoxPoseUseCase.SAFETY - 0.03)
        // and so does the flat sheet, on its width
        assertTrue("flat ${worstAt(1.0).x}", worstAt(1.0).x / region.x > BoxPoseUseCase.SAFETY - 0.03)
        // the box pulls back as it opens: the flat sheet is wider than the folded box
        assertTrue(fit.flat < fit.folded)
        assertEquals(fit.folded, fit.metresPerMm(0.0), 1e-12)
        assertEquals(fit.flat, fit.metresPerMm(1.0), 1e-12)
        // a part 60 mm long shows at roughly a quarter of a metre
        assertTrue("scale ${fit.folded}", fit.folded in 0.0035..0.0055)
    }

    @Test
    fun `a bigger volume gives a bigger box, and yaw is clamped to the allowed range`() {
        val small = useCase.fit(box, lo, hi, Vec3(0.2, 0.2, 0.2))
        val big = useCase.fit(box, lo, hi, Vec3(0.4, 0.4, 0.4))
        assertEquals(2.0, big.folded / small.folded, 1e-9)
        assertEquals(2.0, big.flat / small.flat, 1e-9)
        assertEquals(SceneState.MAX_YAW, useCase.clampYaw(200f), 0f)
        assertEquals(SceneState.MIN_YAW, useCase.clampYaw(-200f), 0f)
        assertEquals(12f, useCase.clampYaw(12f), 0f)
        assertEquals(0.5, useCase.ease(0.5), 1e-9)
        assertEquals(1.0, useCase.ease(3.0), 1e-9)
    }
}
