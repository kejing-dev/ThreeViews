package io.embodit.threeviews.domain.model

import io.embodit.threeviews.domain.geometry.MeshData
import io.embodit.threeviews.domain.geometry.Vec3
import io.embodit.threeviews.domain.geometry.ViewKind

/**
 * Flat quads ready to upload as one mesh: x, y, z per vertex and three indices per triangle.
 * Part space, millimetres. Treat the arrays as read-only.
 */
class RibbonMesh(
    val positions: FloatArray,
    val indices: IntArray,
    val boundsMin: Vec3,
    val boundsMax: Vec3,
) {
    val quadCount: Int get() = indices.size / 6
    val isEmpty: Boolean get() = indices.isEmpty()
}

/** The line work of one projection plane, lifted slightly off the plane toward the part. */
class PlaneLines(
    val view: ViewKind,
    /** 粗实线 */
    val visible: RibbonMesh,
    /** 细虚线 */
    val hidden: RibbonMesh,
    /** 细点画线 */
    val center: RibbonMesh,
    /** The outline of the plane itself; where two planes meet it reads as a projection axis. */
    val border: RibbonMesh,
)

/** Everything the projection box draws for one part, in the folded position. */
class BoxSceneSpec(
    val partId: String,
    val box: BoxLayout,
    /** The solid itself, for the lit part in the middle of the box. */
    val mesh: MeshData,
    val planes: List<PlaneLines>,
    /** Projection rays per view, as crossed ribbons so they read from any side. */
    val rays: Map<ViewKind, RibbonMesh>,
    /** The sharp edges of the part, as crossed ribbons. */
    val edges: RibbonMesh,
) {
    val partMin: Vec3 get() = mesh.boundsMin
    val partMax: Vec3 get() = mesh.boundsMax
    val partCenter: Vec3 get() = (partMin + partMax) * 0.5

    fun plane(view: ViewKind): PlaneLines = planes.first { it.view == view }
}

/**
 * Where everything stands for one unfold progress. Angles in degrees, right-handed:
 * positive pitch turns +Y toward +Z, positive yaw turns +Z toward +X.
 */
data class BoxPose(
    /** The point of part space that sits at the centre of the volume. */
    val pivot: Vec3,
    /** Rotation of the whole box about the vertical axis. */
    val yawDeg: Double,
    /** Forward tilt of the whole box, so the viewer looks down onto plane H. */
    val tiltDeg: Double,
    /** Opening angle of the H and W hinges: 0 folded, 90 flat. */
    val hingeDeg: Double,
    /** Centre of the part. While the planes open, the part moves to the empty quadrant of the sheet. */
    val partPosition: Vec3,
    val partYawDeg: Double,
    val partTiltDeg: Double,
    val partScale: Double,
)

/** The part as a small pictorial view in the empty fourth quadrant of the unfolded sheet. */
data class Pictorial(val center: Vec3, val scale: Double, val radius: Double)

/**
 * Result of fitting the box into the volume. The folded box is more compact than the flat sheet,
 * so each moment of the unfold has its own largest scale: the box pulls back a little as it opens.
 */
data class BoxFit(
    /**
     * Uniform scale of the scene root, metres of volume per millimetre of part, at evenly spaced
     * unfold values from 0 (folded) to 1 (flat).
     */
    val scales: List<Double>,
    /** Largest distance of the folded box from the volume centre on each axis over every yaw, millimetres. */
    val foldedReachMm: Vec3,
    /** The axis that limits the folded box: 'X', 'Y' or 'Z'. */
    val limitingAxis: Char,
) {
    val folded: Double get() = scales.first()
    val flat: Double get() = scales.last()

    /** The scale for an eased unfold value in 0..1, interpolated between the samples. */
    fun metresPerMm(unfold: Double): Double {
        val at = unfold.coerceIn(0.0, 1.0) * (scales.size - 1)
        val i = at.toInt().coerceAtMost(scales.size - 2)
        return scales[i] + (scales[i + 1] - scales[i]) * (at - i)
    }
}
