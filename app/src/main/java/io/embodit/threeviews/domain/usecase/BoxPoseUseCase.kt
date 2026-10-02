package io.embodit.threeviews.domain.usecase

import io.embodit.threeviews.domain.geometry.Vec3
import io.embodit.threeviews.domain.model.BoxFit
import io.embodit.threeviews.domain.model.BoxLayout
import io.embodit.threeviews.domain.model.BoxPose
import io.embodit.threeviews.domain.model.Pictorial
import io.embodit.threeviews.domain.model.SceneState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The motion of the projection box. Folded, the three planes stand around the part and the whole
 * box is turned and tilted like the textbook figure. Unfolding swings H down about OX and W to the
 * right about OZ until the three planes lie in one sheet facing the viewer; meanwhile the part
 * leaves the corner and settles in the empty fourth quadrant as a small pictorial view.
 */
class BoxPoseUseCase {

    fun pictorial(box: BoxLayout, partMin: Vec3, partMax: Vec3): Pictorial {
        val radius = (partMax - partMin).length() / 2
        val scale = min(1.0, PICTORIAL_FILL * box.depthZ / 2 / radius)
        val center = Vec3(
            box.rightX + box.depthZ / 2,
            box.bottomY - box.depthZ / 2,
            box.backZ + radius * scale + PICTORIAL_CLEARANCE,
        )
        return Pictorial(center, scale, radius)
    }

    /** The pose for an eased unfold progress in 0..1 and the yaw the user has chosen. */
    fun pose(box: BoxLayout, partMin: Vec3, partMax: Vec3, unfold: Double, yawDeg: Double): BoxPose {
        val e = unfold.coerceIn(0.0, 1.0)
        val pict = pictorial(box, partMin, partMax)
        val folded = box.center
        val flat = Vec3(
            box.minX + (box.lengthX + box.depthZ) / 2,
            box.topY - (box.heightY + box.depthZ) / 2,
            box.backZ + (pict.radius * pict.scale + PICTORIAL_CLEARANCE) / 2,
        )
        val partCenter = (partMin + partMax) * 0.5
        return BoxPose(
            pivot = folded.lerp(flat, e),
            yawDeg = yawDeg * (1 - e),
            tiltDeg = TILT_DEG * (1 - e),
            hingeDeg = 90.0 * e,
            partPosition = partCenter.lerp(pict.center, e),
            partYawDeg = PICTORIAL_YAW_DEG * e,
            partTiltDeg = PICTORIAL_TILT_DEG * e,
            partScale = 1 + (pict.scale - 1) * e,
        )
    }

    /** Smoothstep: the planes start and stop gently. */
    fun ease(t: Double): Double {
        val c = t.coerceIn(0.0, 1.0)
        return c * c * (3 - 2 * c)
    }

    /**
     * The corners of the three planes and of the part for [pose], relative to the centre of the
     * volume, millimetres. These are the points that must stay inside the volume.
     */
    fun extremePoints(box: BoxLayout, partMin: Vec3, partMax: Vec3, pose: BoxPose): List<Vec3> {
        val hinge = Math.toRadians(pose.hingeDeg)
        val hingeH = Vec3(0.0, box.bottomY, box.backZ)
        val hingeW = Vec3(box.rightX, 0.0, box.backZ)
        val points = ArrayList<Vec3>(20)
        // V does not move.
        for (x in doubleArrayOf(box.minX, box.rightX)) for (y in doubleArrayOf(box.bottomY, box.topY)) points.add(Vec3(x, y, box.backZ))
        // H swings down about OX.
        for (x in doubleArrayOf(box.minX, box.rightX)) for (z in doubleArrayOf(box.backZ, box.frontZ)) {
            val p = Vec3(x, box.bottomY, z)
            points.add(Vec3(x, 0.0, 0.0) + hingeH + rotateX(Vec3(0.0, p.y - hingeH.y, p.z - hingeH.z), hinge))
        }
        // W swings to the right about OZ.
        for (y in doubleArrayOf(box.bottomY, box.topY)) for (z in doubleArrayOf(box.backZ, box.frontZ)) {
            val p = Vec3(box.rightX, y, z)
            points.add(Vec3(0.0, y, 0.0) + hingeW + rotateY(Vec3(p.x - hingeW.x, 0.0, p.z - hingeW.z), hinge))
        }
        // The part, on its way to the pictorial corner.
        val partCenter = (partMin + partMax) * 0.5
        val partYaw = Math.toRadians(pose.partYawDeg)
        val partTilt = Math.toRadians(pose.partTiltDeg)
        for (x in doubleArrayOf(partMin.x, partMax.x)) for (y in doubleArrayOf(partMin.y, partMax.y)) for (z in doubleArrayOf(partMin.z, partMax.z)) {
            val local = (Vec3(x, y, z) - partCenter) * pose.partScale
            points.add(pose.partPosition + rotateX(rotateY(local, partYaw), partTilt))
        }
        val yaw = Math.toRadians(pose.yawDeg)
        val tilt = Math.toRadians(pose.tiltDeg)
        return points.map { rotateX(rotateY(it - pose.pivot, yaw), tilt) }
    }

    /**
     * The largest uniform scale at which the box stays inside the volume for every yaw the user can
     * choose, worked out separately for each moment of the unfold. [regionHalf] is the half size of
     * the volume in metres.
     */
    fun fit(box: BoxLayout, partMin: Vec3, partMax: Vec3, regionHalf: Vec3, safety: Double = SAFETY): BoxFit {
        require(regionHalf.x > 0 && regionHalf.y > 0 && regionHalf.z > 0) { "the volume has no size" }
        val reaches = (0..UNFOLD_SAMPLES).map { step -> reach(box, partMin, partMax, step.toDouble() / UNFOLD_SAMPLES) }
        val scales = reaches.map { r -> safety * min(regionHalf.x / r.x, min(regionHalf.y / r.y, regionHalf.z / r.z)) }
        val folded = reaches.first()
        val sx = regionHalf.x / folded.x
        val sy = regionHalf.y / folded.y
        val sz = regionHalf.z / folded.z
        val axis = when (min(sx, min(sy, sz))) { sx -> 'X'; sy -> 'Y'; else -> 'Z' }
        return BoxFit(scales = scales, foldedReachMm = folded, limitingAxis = axis)
    }

    /** How far the box reaches from the centre of the volume on each axis at one unfold value, over every yaw. */
    private fun reach(box: BoxLayout, partMin: Vec3, partMax: Vec3, unfold: Double): Vec3 {
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var yaw = SceneState.MIN_YAW.toDouble()
        while (yaw <= SceneState.MAX_YAW + 1e-9) {
            for (p in extremePoints(box, partMin, partMax, pose(box, partMin, partMax, unfold, yaw))) {
                x = max(x, abs(p.x)); y = max(y, abs(p.y)); z = max(z, abs(p.z))
            }
            yaw += YAW_SAMPLE_STEP
        }
        return Vec3(x, y, z)
    }

    fun clampYaw(yawDeg: Float): Float = yawDeg.coerceIn(SceneState.MIN_YAW, SceneState.MAX_YAW)

    private fun rotateX(p: Vec3, a: Double): Vec3 {
        val c = cos(a); val s = sin(a)
        return Vec3(p.x, p.y * c - p.z * s, p.y * s + p.z * c)
    }

    private fun rotateY(p: Vec3, a: Double): Vec3 {
        val c = cos(a); val s = sin(a)
        return Vec3(p.x * c + p.z * s, p.y, -p.x * s + p.z * c)
    }

    companion object {
        /** Forward tilt of the folded box: the viewer looks slightly down onto plane H. */
        const val TILT_DEG = 15.0

        /** The pictorial view shows the front, top and left faces, as textbook figures do. */
        const val PICTORIAL_YAW_DEG = 35.0
        const val PICTORIAL_TILT_DEG = 20.0

        /** Share of the fourth quadrant the pictorial view may fill. */
        const val PICTORIAL_FILL = 0.9
        const val PICTORIAL_CLEARANCE = 2.0

        /** Share of the volume the box may use; the rest is breathing room at the edges. */
        const val SAFETY = 0.94
        private const val UNFOLD_SAMPLES = 20
        private const val YAW_SAMPLE_STEP = 5.0

        /** Seconds the planes take to swing open or shut. */
        const val UNFOLD_SECONDS = 0.9
    }
}
