package io.embodit.threeviews.domain.model

import io.embodit.threeviews.domain.geometry.Vec3

/**
 * The three projection planes around a part, in part space (millimetres).
 * V stands behind the part, H lies below it, W stands to its right (first-angle projection).
 * The planes meet at the corner ([rightX], [bottomY], [backZ]).
 */
data class BoxLayout(
    val margin: Double,
    /** z of plane V. */
    val backZ: Double,
    /** y of plane H. */
    val bottomY: Double,
    /** x of plane W. */
    val rightX: Double,
    /** The other extents shared by the planes. */
    val minX: Double,
    val topY: Double,
    val frontZ: Double,
) {
    val lengthX: Double get() = rightX - minX
    val heightY: Double get() = topY - bottomY
    val depthZ: Double get() = frontZ - backZ
    val center: Vec3 get() = Vec3((minX + rightX) / 2, (bottomY + topY) / 2, (backZ + frontZ) / 2)
}

/** A projection ray: from a point of the part to its image on a plane. Part space, millimetres. */
data class ProjectionRay(val from: Vec3, val to: Vec3)
