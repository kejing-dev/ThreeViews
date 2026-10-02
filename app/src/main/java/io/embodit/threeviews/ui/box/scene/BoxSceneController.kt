package io.embodit.threeviews.ui.box.scene

import android.util.Log
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import io.embodit.threeviews.domain.geometry.Vec3
import io.embodit.threeviews.domain.geometry.ViewKind
import io.embodit.threeviews.domain.model.BoxFit
import io.embodit.threeviews.domain.model.BoxPose
import io.embodit.threeviews.domain.model.BoxSceneSpec
import io.embodit.threeviews.domain.usecase.BoxPoseUseCase
import io.embodit.threeviews.ui.box.BoxUiState
import kotlin.math.abs

/**
 * Owns the entities of the projection box.
 *
 * ```
 * SpatialView
 * └ placementOwner            identity; bounds evidence is measured here
 *   └ fittedSceneRoot         the one uniform fit: metres per millimetre of part
 *     └ tilt                  forward tilt of the folded box
 *       └ turn                yaw chosen by the user
 *         └ content           part space, millimetres; replaced for every part
 *           ├ plane V + its line work
 *           ├ hinge H → plane H + its line work     (swings down about OX)
 *           ├ hinge W → plane W + its line work     (swings right about OZ)
 *           ├ rays (one entity per view)
 *           └ part mount → tilt → turn → scale → solid, edges, tap target
 * ```
 *
 * All calls come from the main thread: [install] and [show] from Compose when the part or the
 * options change, [onFrame] from the ECS system. Discrete changes replace or toggle entities; the
 * motion is driven per frame.
 */
internal class BoxSceneController(private val poses: BoxPoseUseCase = BoxPoseUseCase()) {

    val placementOwner: Entity = Entity().apply { setName("placementOwner") }
    private val fittedSceneRoot = Entity().apply { setName("fittedSceneRoot") }
    private val tilt = Entity().apply { setName("tilt") }
    private val turn = Entity().apply { setName("turn") }

    private var resources: BoxResources? = null
    private var meshes: BoxMeshes? = null
    private var retired: BoxMeshes? = null

    private var content: Entity? = null
    private var hingeH: Entity? = null
    private var hingeW: Entity? = null
    private var partMount: Entity? = null
    private var partTilt: Entity? = null
    private var partTurn: Entity? = null
    private var partScale: Entity? = null
    private val hiddenLines = HashMap<ViewKind, Entity>()
    private val rays = HashMap<ViewKind, Entity>()

    /** 2D name tags of the planes. They belong to the SpatialView and outlive every part. */
    private var labels: Map<ViewKind, Entity> = emptyMap()

    private var state = BoxUiState()
    private var regionHalf: Vec3? = null
    private var fit: BoxFit? = null

    /** Linear unfold progress 0..1 and the yaw currently shown; both chase the state every frame. */
    private var progress = 0.0
    private var yaw = 0.0
    private var applied: BoxPose? = null
    private var appliedScale = 0.0
    private var raysShown: Set<ViewKind>? = null

    /** The part currently in the box. */
    val spec: BoxSceneSpec? get() = meshes?.spec

    init {
        placementOwner.addChild(fittedSceneRoot)
        fittedSceneRoot.addChild(tilt)
        tilt.addChild(turn)
        // Nothing is shown before the first fit: the content is in millimetres and would fill the room.
        fittedSceneRoot.enabled = false
    }

    /** The half size of the volume in metres, measured by the SpatialView. Refits when it changes. */
    fun setRegion(half: Vec3) {
        val known = regionHalf
        if (known != null && abs(known.x - half.x) < 1e-4 && abs(known.y - half.y) < 1e-4 && abs(known.z - half.z) < 1e-4) return
        regionHalf = half
        refit()
    }

    /** Hands over the name tags of the three planes, created by the SpatialView as attachments. */
    fun setLabels(tags: Map<ViewKind, Entity>) {
        labels = tags
        meshes?.let { placeLabels(it.spec) }
        appliedScale = 0.0
        applyFrame()
    }

    /** True once the shared materials exist; [install] needs them. */
    val hasResources: Boolean get() = resources != null

    /** Hands over the shared materials, created off the main thread. A second set is closed again. */
    fun provide(created: BoxResources) {
        if (resources == null) resources = created else created.close()
    }

    /** Puts another part into the box. The controller owns [next] from here on. */
    fun install(next: BoxMeshes) {
        val first = meshes == null
        // The name tags must not go down with the old part.
        labels.values.forEach { it.removeFromParent() }
        content?.destroy()
        hiddenLines.clear()
        rays.clear()
        // The part shown before the previous one is certainly off screen by now.
        retired?.close()
        retired = meshes
        meshes = next
        assemble(next)
        if (first) {
            // Open in the state the user left: no swing-in for a box that is already flat.
            progress = if (state.unfolded) 1.0 else 0.0
            yaw = state.yawDegrees.toDouble()
        }
        refit()
        applyOptions()
    }

    fun show(next: BoxUiState) {
        state = next
        applyOptions()
    }

    /** Advances the unfold and the turn. Called once per rendered frame by [BoxAnimationSystem]. */
    fun onFrame(deltaSeconds: Float) {
        if (meshes == null) return
        val dt = deltaSeconds.coerceIn(0f, 0.1f).toDouble()
        val target = if (state.unfolded) 1.0 else 0.0
        if (progress != target) {
            val step = dt / BoxPoseUseCase.UNFOLD_SECONDS
            progress = if (target > progress) minOf(target, progress + step) else maxOf(target, progress - step)
        }
        val yawTarget = state.yawDegrees.toDouble()
        if (yaw != yawTarget) {
            yaw += (yawTarget - yaw) * minOf(1.0, dt * YAW_FOLLOW_PER_SECOND)
            if (abs(yawTarget - yaw) < 0.05) yaw = yawTarget
        }
        applyFrame()
    }

    fun dispose() {
        labels.values.forEach { it.removeFromParent() }
        labels = emptyMap()
        placementOwner.destroy()
        meshes?.close()
        retired?.close()
        resources?.close()
        meshes = null
        retired = null
        resources = null
        content = null
    }

    /** Planned and remeasured bounds of the box in the space of [placementOwner], for the scene report. */
    fun evidence(): String {
        val s = spec ?: return "no part"
        val f = fit ?: return "not fitted"
        val region = regionHalf ?: return "no region"
        val visual = fittedSceneRoot.getVisualBounds(placementOwner, true, true)
        val planned = poses.extremePoints(s.box, s.partMin, s.partMax, poses.pose(s.box, s.partMin, s.partMax, poses.ease(progress), yaw))
        val scale = f.metresPerMm(poses.ease(progress))
        val pMin = Vec3(planned.minOf { it.x }, planned.minOf { it.y }, planned.minOf { it.z }) * scale
        val pMax = Vec3(planned.maxOf { it.x }, planned.maxOf { it.y }, planned.maxOf { it.z }) * scale
        val eps = EVIDENCE_EPSILON
        val inside = abs(visual.min.x) <= region.x + eps && abs(visual.max.x) <= region.x + eps &&
            abs(visual.min.y) <= region.y + eps && abs(visual.max.y) <= region.y + eps &&
            abs(visual.min.z) <= region.z + eps && abs(visual.max.z) <= region.z + eps
        return "sceneRevision=${s.partId}@${region.x}x${region.y}x${region.z} epsilon=$eps " +
            "part=${s.partId} unfold=$progress yaw=$yaw boundsOwner=placementOwner " +
            "safeRegionHalf=[${region.x}, ${region.y}, ${region.z}] uniformScale=$scale limitingAxis=${f.limitingAxis} " +
            "plannedMin=[${pMin.x}, ${pMin.y}, ${pMin.z}] plannedMax=[${pMax.x}, ${pMax.y}, ${pMax.z}] " +
            "visualMin=${visual.min} visualMax=${visual.max} numericStatus=${if (inside) "PASS" else "FAIL"}"
    }

    private fun refit() {
        val s = spec ?: return
        val region = regionHalf ?: return
        val f = poses.fit(s.box, s.partMin, s.partMax, region)
        fit = f
        applied = null
        appliedScale = 0.0
        val wasHidden = !fittedSceneRoot.enabled
        fittedSceneRoot.enabled = true
        // An attachment panel added under a disabled entity never shows, so the name tags are put
        // on their planes again once the scene is switched on.
        if (wasHidden) placeLabels(s)
        Log.i(TAG, "fit part=${s.partId} regionHalf=$region folded=${f.folded} flat=${f.flat} foldedReachMm=${f.foldedReachMm} limit=${f.limitingAxis}")
        applyFrame()
    }

    private fun applyOptions() {
        val res = resources ?: return
        for (view in ViewKind.entries) {
            hiddenLines[view]?.enabled = state.showHidden
            res.planes.getValue(view).setOpacity(
                if (view in state.emphasised) BoxResources.PLANE_OPACITY else BoxResources.PLANE_OPACITY_BACKGROUND,
            )
        }
        applyFrame()
    }

    private fun applyFrame() {
        val s = spec ?: return
        val eased = poses.ease(progress)
        // The one fit transform of the scene root. It follows the unfold: the flat sheet is wider than the folded box.
        val scale = fit?.metresPerMm(eased) ?: return
        if (scale != appliedScale) {
            appliedScale = scale
            val k = scale.toFloat()
            fittedSceneRoot.transform().setScaleVector(Vector3(k, k, k))
        }
        val pose = poses.pose(s.box, s.partMin, s.partMax, eased, yaw)
        if (pose != applied) {
            applied = pose
            tilt.transform().setEulerAngles(EulerAngles(pitch = pose.tiltDeg.toFloat()))
            turn.transform().setEulerAngles(EulerAngles(yaw = pose.yawDeg.toFloat()))
            content?.transform()?.setPosition((-pose.pivot).toVector3())
            hingeH?.transform()?.setEulerAngles(EulerAngles(pitch = pose.hingeDeg.toFloat()))
            hingeW?.transform()?.setEulerAngles(EulerAngles(yaw = pose.hingeDeg.toFloat()))
            partMount?.transform()?.setPosition(pose.partPosition.toVector3())
            partTilt?.transform()?.setEulerAngles(EulerAngles(pitch = pose.partTiltDeg.toFloat()))
            partTurn?.transform()?.setEulerAngles(EulerAngles(yaw = pose.partYawDeg.toFloat()))
            val k = pose.partScale.toFloat()
            partScale?.transform()?.setScaleVector(Vector3(k, k, k))
        }
        // Rays connect the part to standing planes; they leave as soon as a plane starts to move.
        val shown = if (progress == 0.0) state.rays else emptySet()
        if (shown != raysShown) {
            raysShown = shown
            for ((view, entity) in rays) entity.enabled = view in shown
        }
    }

    private fun assemble(next: BoxMeshes) {
        val started = android.os.SystemClock.elapsedRealtime()
        val res = resources ?: BoxResources().also { resources = it }
        val spec = next.spec
        val box = spec.box
        applied = null
        raysShown = null

        val root = Entity().apply { setName("content") }
        val hingeHPosition = Vec3(0.0, box.bottomY, box.backZ)
        val hingeWPosition = Vec3(box.rightX, 0.0, box.backZ)
        val h = Entity().apply { setName("hingeH"); transform().setPosition(hingeHPosition.toVector3()) }
        val w = Entity().apply { setName("hingeW"); transform().setPosition(hingeWPosition.toVector3()) }
        root.addChild(h)
        root.addChild(w)

        for (view in ViewKind.entries) {
            val plane = next.planes.getValue(view)
            val (parent, origin) = when (view) {
                ViewKind.FRONT -> root to Vec3.ZERO
                ViewKind.TOP -> h to hingeHPosition
                ViewKind.LEFT -> w to hingeWPosition
            }
            // The face of the plate that carries the drawing lies exactly on the projection plane.
            val half = BoxMeshes.PLATE_THICKNESS / 2
            val center = when (view) {
                ViewKind.FRONT -> Vec3(box.center.x, box.center.y, box.backZ - half)
                ViewKind.TOP -> Vec3(box.center.x, box.bottomY - half, box.center.z)
                ViewKind.LEFT -> Vec3(box.rightX + half, box.center.y, box.center.z)
            }
            val plate = ModelEntity(plane.plate, res.planes.getValue(view)).apply {
                setName(planeName(view))
                transform().setPosition((center - origin).toVector3())
                components.set(tapTarget(plane.plateShape, next))
                components.set(InteractableComponent())
            }
            parent.addChild(plate)

            fun add(mesh: MeshResource?, material: Material, name: String): Entity? {
                mesh ?: return null
                val entity = ModelEntity(mesh, material).apply {
                    setName(name + viewSuffix(view))
                    transform().setPosition((-origin).toVector3())
                }
                parent.addChild(entity)
                return entity
            }
            add(plane.border, res.inkSoft, "border")
            add(plane.visible, res.ink, "visible")
            add(plane.center, res.centerLine, "center")
            add(plane.hidden, res.ink, "hidden")?.let { hiddenLines[view] = it }
            plane.rays?.let { mesh ->
                val entity = ModelEntity(mesh, res.ray).apply { setName("rays" + viewSuffix(view)) }
                root.addChild(entity)
                rays[view] = entity
            }
        }

        val mount = Entity().apply { setName("partMount") }
        val mountTilt = Entity().apply { setName("partTilt") }
        val mountTurn = Entity().apply { setName("partTurn") }
        val mountScale = Entity().apply { setName("partScale") }
        root.addChild(mount)
        mount.addChild(mountTilt)
        mountTilt.addChild(mountTurn)
        mountTurn.addChild(mountScale)
        val offset = (-spec.partCenter).toVector3()
        mountScale.addChild(ModelEntity(next.solid, res.part).apply { setName("partSolid"); transform().setPosition(offset) })
        next.edges?.let { mountScale.addChild(ModelEntity(it, res.ink).apply { setName("partEdges"); transform().setPosition(offset) }) }
        mountScale.addChild(
            Entity().apply {
                setName(PART_NAME)
                components.set(tapTarget(next.partShape, next))
                components.set(InteractableComponent())
            },
        )

        turn.addChild(root)
        content = root
        hingeH = h
        hingeW = w
        placeLabels(spec)
        partMount = mount
        partTilt = mountTilt
        partTurn = mountTurn
        partScale = mountScale
        Log.i(TAG, "scene assembled part=${spec.partId} in ${android.os.SystemClock.elapsedRealtime() - started} ms")
    }

    /**
     * Puts each name tag in the outer corner of its plane, lying on the plane and reading upright
     * from the part's side: V top-left, H front-left, W top-front. Unfolded they end up in the
     * corners of the sheet.
     */
    private fun placeLabels(spec: BoxSceneSpec) {
        val box = spec.box
        val h = hingeH ?: return
        val w = hingeW ?: return
        val root = content ?: return
        val pad = LABEL_PAD
        val lift = LABEL_LIFT
        // A tag is part of the scene: it grows and shrinks with the box.
        val size = LABEL_UNITS
        labels.values.forEach { it.transform().setScaleVector(Vector3(size, size, size)) }
        labels[ViewKind.FRONT]?.let { tag ->
            root.addChild(tag)
            tag.transform().setPosition(Vec3(box.minX + pad, box.topY - pad, box.backZ + lift).toVector3())
            tag.transform().setEulerAngles(EulerAngles())
        }
        labels[ViewKind.TOP]?.let { tag ->
            h.addChild(tag)
            tag.transform().setPosition((Vec3(box.minX + pad, box.bottomY + lift, box.frontZ - pad) - Vec3(0.0, box.bottomY, box.backZ)).toVector3())
            tag.transform().setEulerAngles(EulerAngles(pitch = -90f))
        }
        labels[ViewKind.LEFT]?.let { tag ->
            w.addChild(tag)
            tag.transform().setPosition((Vec3(box.rightX - lift, box.topY - pad, box.frontZ - pad) - Vec3(box.rightX, 0.0, box.backZ)).toVector3())
            tag.transform().setEulerAngles(EulerAngles(yaw = -90f))
        }
    }

    private fun tapTarget(shape: ShapeResource, meshes: BoxMeshes): CollisionComponent =
        CollisionComponent(collisionShape = listOf(shape), physicsMaterial = meshes.physics)

    private fun Entity.transform(): TransformComponent = components[TransformComponent::class.java]!!

    companion object {
        const val TAG = "ThreeViews"
        const val PART_NAME = "part"

        /**
         * Scale of a name tag in part space. An attachment panel is about 0.05 units tall at its
         * natural size (it is laid out in metres); this makes it about 7 mm of part space, a
         * fifteenth of a plane's height.
         */
        private const val LABEL_UNITS = 140f

        /** Distance of a name tag from the edges of its plane, and how far it floats above it, millimetres. */
        private const val LABEL_PAD = 2.5
        private const val LABEL_LIFT = 0.6

        /** Tolerance of the containment check in the scene report, metres. */
        private const val EVIDENCE_EPSILON = 0.001

        /** How quickly the shown yaw follows the chosen yaw. */
        private const val YAW_FOLLOW_PER_SECOND = 14.0

        private fun viewSuffix(view: ViewKind): String = view.name.lowercase().replaceFirstChar { it.uppercase() }

        fun planeName(view: ViewKind): String = "plane" + viewSuffix(view)

        /** The view whose plane carries this name, or null for anything else. */
        fun viewOfPlane(name: String?): ViewKind? = ViewKind.entries.firstOrNull { planeName(it) == name }
    }
}
