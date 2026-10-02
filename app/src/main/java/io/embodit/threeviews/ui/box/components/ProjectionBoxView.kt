package io.embodit.threeviews.ui.box.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.pico.spatial.core.ecs.AttachmentPanelComponent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.dsl.registerSystem
import com.pico.spatial.ui.foundation.dsl.unregisterSystem
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import io.embodit.threeviews.R
import io.embodit.threeviews.domain.geometry.Vec3
import io.embodit.threeviews.domain.geometry.ViewKind
import io.embodit.threeviews.ui.box.BoxEvent
import io.embodit.threeviews.ui.box.BoxUiState
import io.embodit.threeviews.ui.box.scene.BoxAnimationSystem
import io.embodit.threeviews.ui.box.scene.BoxMeshes
import io.embodit.threeviews.ui.box.scene.BoxResources
import io.embodit.threeviews.ui.box.scene.BoxSceneController
import io.embodit.threeviews.ui.box.scene.BoxSceneHolder
import io.embodit.threeviews.ui.theme.ThreeViewsColors
import io.embodit.threeviews.ui.theme.ThreeViewsTheme
import io.embodit.threeviews.ui.theme.fixed
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * The projection box as 3D content: the part between its three projection planes.
 *
 * Compose only hands over state changes; the entities live in [BoxSceneController] and the motion
 * runs in [BoxAnimationSystem]. Tapping a plane concentrates on its view, tapping the part shows
 * all three again, and a horizontal drag turns the box about its vertical axis.
 */
@Composable
internal fun ProjectionBoxView(
    state: BoxUiState,
    onEvent: (BoxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    val controller = remember { BoxSceneController() }
    // Read in update so a resized volume is measured and fitted again.
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    DisposableEffect(controller) {
        BoxSceneHolder.controller = controller
        registerSystem<BoxAnimationSystem>()
        onDispose {
            unregisterSystem<BoxAnimationSystem>()
            if (BoxSceneHolder.controller === controller) BoxSceneHolder.controller = null
            controller.dispose()
        }
    }

    // Creating materials, meshes and collision shapes waits for the renderer, so it happens off the
    // main thread. It is never abandoned half way: a part that is no longer wanted is closed
    // instead of installed.
    val scene = state.scene
    LaunchedEffect(controller, scene) {
        if (scene == null || scene === controller.spec) return@LaunchedEffect
        val (resources, meshes) = withContext(NonCancellable + Dispatchers.Default) {
            (if (controller.hasResources) null else BoxResources()) to BoxMeshes.create(scene)
        }
        resources?.let(controller::provide)
        if (isActive) controller.install(meshes) else meshes.close()
    }

    SpatialView(
        modifier = modifier
            .onSizeChanged { viewSize = it }
            .pointerInput(Unit) {
                detectSpatialTapGesture(context = context, targetedToEntity = null) { tap ->
                    val name = tap.targetEntity?.getName()
                    val view = BoxSceneController.viewOfPlane(name)
                    when {
                        view != null -> currentOnEvent(BoxEvent.PlaneTapped(view))
                        name == BoxSceneController.PART_NAME -> currentOnEvent(BoxEvent.PartTapped)
                    }
                }
            }
            .pointerInput(Unit) {
                detectSpatialDragGesture(context) { drag ->
                    // The drag arrives in the volume's own axes. Seen from the side, a sideways pull
                    // has a depth component too, so the whole horizontal travel counts.
                    val amount = drag.dragAmount
                    val travel = hypot(amount.x, amount.z) * sign(amount.x)
                    currentOnEvent(BoxEvent.Turned(travel * DEGREES_PER_DRAG_UNIT))
                }
            },
        attachments = {
            AttachmentPanel(id = ViewKind.FRONT, alignment = AttachmentPanelComponent.Alignment.TOP_LEFT) { PlaneLabel(R.string.plane_front) }
            AttachmentPanel(id = ViewKind.TOP, alignment = AttachmentPanelComponent.Alignment.BOTTOM_LEFT) { PlaneLabel(R.string.plane_top) }
            AttachmentPanel(id = ViewKind.LEFT, alignment = AttachmentPanelComponent.Alignment.TOP_RIGHT) { PlaneLabel(R.string.plane_left) }
        },
        initial = { content, attachments ->
            content.addEntity(controller.placementOwner)
            controller.setLabels(ViewKind.entries.mapNotNull { view -> attachments.entity(view)?.let { view to it } }.toMap())
        },
        update = { content, _ ->
            if (viewSize != IntSize.Zero) {
                // The top-left-back corner of the view in metres gives the half size of the volume.
                val corner = content.convertPosition(Vector3.ZERO, ViewCoordinateSpace.Local, content.localSpatialCoordinateSpace)
                controller.setRegion(Vec3(abs(corner.x).toDouble(), abs(corner.y).toDouble(), abs(corner.z).toDouble()))
            }
            controller.show(state)
        },
    )
}

/** The name of a projection plane, written on the plane itself. */
@Composable
private fun PlaneLabel(text: Int) {
    ThreeViewsTheme {
        // Drawn large and scaled in the scene, so the enlarged panel stays sharp.
        val style = PicoTheme.typography.titleSmall
        Text(
            text = stringResource(text),
            style = style.copy(fontSize = style.fontSize * LabelFontScale, lineHeight = style.lineHeight * LabelFontScale),
            color = ThreeViewsColors.Ink.fixed(),
            maxLines = 1,
            // A paper tag: a panel needs a ground of its own to show up against the translucent plane.
            modifier = Modifier
                .clip(RoundedCornerShape(LabelCorner))
                .background(ThreeViewsColors.Paper.fixed())
                .padding(horizontal = LabelPadding, vertical = LabelPadding / 3),
        )
    }
}

private const val LabelFontScale = 3
private val LabelPadding = 18.dp
private val LabelCorner = 12.dp

/** Degrees of turn per unit of horizontal drag: pulling across the whole volume turns the box about three quarters round. */
private const val DEGREES_PER_DRAG_UNIT = 0.07f
