package io.embodit.threeviews.domain.geometry

/** Everything the app needs to show one part: the render mesh and its three orthographic views. */
class PartDrawing(
    val part: PartDefinition,
    val mesh: MeshData,
    /** 主视图 */
    val front: ViewDrawing,
    /** 俯视图 */
    val top: ViewDrawing,
    /** 左视图 */
    val left: ViewDrawing,
) {
    fun view(kind: ViewKind): ViewDrawing = when (kind) {
        ViewKind.FRONT -> front
        ViewKind.TOP -> top
        ViewKind.LEFT -> left
    }

    /** The views in drawing order: front, top, left. */
    val views: List<ViewDrawing> get() = listOf(front, top, left)
}

/**
 * Builds the solid of the part with id [partId] and computes its mesh and three views.
 *
 * Deterministic and free of shared mutable state: safe to call from any thread, and equal ids give
 * equal results, so the caller may cache by id. Throws [IllegalArgumentException] for an unknown id.
 */
fun computeDrawing(partId: String): PartDrawing = computeDrawing(PartLibrary[partId])

/** Same as [computeDrawing] for a part definition, which need not come from [PartLibrary]. */
fun computeDrawing(part: PartDefinition): PartDrawing {
    val geometry = PartGeometry.of(part.build())
    return PartDrawing(
        part = part,
        mesh = MeshData.from(geometry),
        front = ViewProjector.project(geometry, part.axes, ViewKind.FRONT),
        top = ViewProjector.project(geometry, part.axes, ViewKind.TOP),
        left = ViewProjector.project(geometry, part.axes, ViewKind.LEFT),
    )
}
