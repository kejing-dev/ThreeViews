package io.embodit.threeviews.ui.box.scene

import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import io.embodit.threeviews.domain.geometry.Vec3
import io.embodit.threeviews.domain.geometry.ViewKind
import io.embodit.threeviews.domain.model.BoxSceneSpec
import io.embodit.threeviews.domain.model.RibbonMesh

/** The meshes of one projection plane. Line sets that are empty for a part have no mesh. */
internal class PlaneMeshes(
    val plate: MeshResource,
    /** Collision shape of the plate: the tap target of this view. */
    val plateShape: ShapeResource,
    val border: MeshResource?,
    val visible: MeshResource?,
    val hidden: MeshResource?,
    val center: MeshResource?,
    val rays: MeshResource?,
)

/**
 * Every mesh and collision shape the box needs for one part. Creating a resource waits for the
 * renderer, so [create] is meant for a background thread; the entities are then assembled on the
 * main thread in no time.
 *
 * All of them are persisted with toGlobal, so their lifetime is this object's and not that of
 * whichever entity happens to use them: [close] releases them, whether or not the part was ever shown.
 */
internal class BoxMeshes(
    val spec: BoxSceneSpec,
    val planes: Map<ViewKind, PlaneMeshes>,
    val solid: MeshResource,
    val edges: MeshResource?,
    /** Collision shape around the part: the tap target that shows all three views again. */
    val partShape: ShapeResource,
    val physics: PhysicsMaterialResource,
) {
    fun close() {
        for (plane in planes.values) {
            listOfNotNull(plane.plate, plane.border, plane.visible, plane.hidden, plane.center, plane.rays).forEach { it.close() }
            plane.plateShape.close()
        }
        solid.close()
        edges?.close()
        partShape.close()
        physics.close()
    }

    companion object {
        /** Thickness of a projection plane, millimetres of part space. */
        const val PLATE_THICKNESS = 0.4

        fun create(spec: BoxSceneSpec): BoxMeshes {
            val box = spec.box
            val id = spec.partId
            fun RibbonMesh.orNull(name: String): MeshResource? = if (isEmpty) null else toMeshResource("$name-$id")
            val planes = ViewKind.entries.associateWith { view ->
                val size = when (view) {
                    ViewKind.FRONT -> Vec3(box.lengthX, box.heightY, PLATE_THICKNESS)
                    ViewKind.TOP -> Vec3(box.lengthX, PLATE_THICKNESS, box.depthZ)
                    ViewKind.LEFT -> Vec3(PLATE_THICKNESS, box.heightY, box.depthZ)
                }
                val lines = spec.plane(view)
                PlaneMeshes(
                    plate = MeshResource.createBox(size.toVector3()).apply { toGlobal() },
                    plateShape = ShapeResource.createBox(size.toVector3()).apply { toGlobal() },
                    border = lines.border.orNull("border-${view.name}"),
                    visible = lines.visible.orNull("visible-${view.name}"),
                    hidden = lines.hidden.orNull("hidden-${view.name}"),
                    center = lines.center.orNull("center-${view.name}"),
                    rays = spec.rays.getValue(view).orNull("rays-${view.name}"),
                )
            }
            return BoxMeshes(
                spec = spec,
                planes = planes,
                solid = spec.mesh.toMeshResource("part-$id"),
                edges = spec.edges.orNull("edges"),
                partShape = ShapeResource.createBox((spec.partMax - spec.partMin).toVector3()).apply { toGlobal() },
                physics = PhysicsMaterialResource().apply { toGlobal() },
            )
        }
    }
}
