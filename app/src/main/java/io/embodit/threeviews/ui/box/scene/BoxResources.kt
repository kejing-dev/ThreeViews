package io.embodit.threeviews.ui.box.scene

import androidx.compose.ui.graphics.Color
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import io.embodit.threeviews.domain.geometry.MeshData
import io.embodit.threeviews.domain.geometry.Vec3
import io.embodit.threeviews.domain.geometry.ViewKind
import io.embodit.threeviews.domain.model.RibbonMesh
import io.embodit.threeviews.ui.theme.ThreeViewsColors

/**
 * Materials shared by every part the box shows, created once per scene lifetime, plus the
 * conversions from the pure geometry of the domain layer to SDK meshes.
 *
 * The SDK releases a resource together with the last entity that uses it. These materials outlive
 * the entities of any one part, so they are persisted with toGlobal and closed by [close].
 */
internal class BoxResources {
    val part: PhysicallyBasedMaterial = PhysicallyBasedMaterial.create().apply {
        setBaseColor(ThreeViewsColors.PartBody.toColor4())
        // A little light of its own keeps the faces turned away from the room lights readable.
        setEmissiveColor(ThreeViewsColors.PartBody.copy(red = PART_GLOW * ThreeViewsColors.PartBody.red, green = PART_GLOW * ThreeViewsColors.PartBody.green, blue = PART_GLOW * ThreeViewsColors.PartBody.blue).toColor4())
        setRoughness(0.85f)
        setMetallic(0f)
        toGlobal()
    }
    val ink: UnlitMaterial = line(ThreeViewsColors.Ink)
    val inkSoft: UnlitMaterial = line(ThreeViewsColors.InkSoft)
    val centerLine: UnlitMaterial = line(ThreeViewsColors.CenterLine)
    val ray: UnlitMaterial = line(ThreeViewsColors.RayBlue)

    /** One translucent material per plane, so each can step back on its own. */
    val planes: Map<ViewKind, UnlitMaterial> = mapOf(
        ViewKind.FRONT to plane(ThreeViewsColors.PlaneV),
        ViewKind.TOP to plane(ThreeViewsColors.PlaneH),
        ViewKind.LEFT to plane(ThreeViewsColors.PlaneW),
    )

    fun close() {
        part.close()
        listOf(ink, inkSoft, centerLine, ray).forEach { it.close() }
        planes.values.forEach { it.close() }
    }

    private fun line(color: Color): UnlitMaterial = UnlitMaterial.create().apply {
        setBaseColor(color.toColor4())
        setCullingMode(MaterialCullingMode.NONE)
        setApplyToneMapping(false)
        toGlobal()
    }

    private fun plane(color: Color): UnlitMaterial = UnlitMaterial.create().apply {
        setBaseColor(color.toColor4())
        setBlendingMode(BlendingMode.TRANSPARENT)
        setOpacity(PLANE_OPACITY)
        setCullingMode(MaterialCullingMode.NONE)
        setApplyToneMapping(false)
        toGlobal()
    }

    companion object {
        const val PART_GLOW = 0.38f
        const val PLANE_OPACITY = 0.55f
        const val PLANE_OPACITY_BACKGROUND = 0.16f
    }
}

internal fun Color.toColor4(): Color4 = Color4(red, green, blue, alpha)

internal fun Vec3.toVector3(): Vector3 = Vector3(x.toFloat(), y.toFloat(), z.toFloat())

private fun bounds(min: Vec3, max: Vec3, pad: Double = 0.0): BoundingBox =
    BoundingBox(
        center = ((min + max) * 0.5).toVector3(),
        halfExtent = ((max - min) * 0.5 + Vec3(pad, pad, pad)).toVector3(),
    )

private fun FloatArray.toVectors(): List<Vector3> = List(size / 3) { Vector3(this[it * 3], this[it * 3 + 1], this[it * 3 + 2]) }

/** The lit solid: flat shaded, positions in part space (millimetres). */
internal fun MeshData.toMeshResource(name: String): MeshResource {
    val model = MeshModel(positions = positions.toVectors(), triangleIndices = indices.asList(), normals = normals.toVectors())
    MeshModel.validateOrThrow(model)
    return MeshResource.createWithMeshModel(model = model, bounds = bounds(boundsMin, boundsMax), name = name).apply { toGlobal() }
}

/** Line work: unlit quads, drawn from both sides. */
internal fun RibbonMesh.toMeshResource(name: String): MeshResource {
    val model = MeshModel(positions = positions.toVectors(), triangleIndices = indices.asList())
    return MeshResource.createWithMeshModel(model = model, bounds = bounds(boundsMin, boundsMax, pad = 0.05), name = name).apply { toGlobal() }
}
