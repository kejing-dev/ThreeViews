package io.embodit.threeviews.ui.box

import io.embodit.threeviews.domain.geometry.ViewKind
import io.embodit.threeviews.domain.model.BoxSceneSpec
import io.embodit.threeviews.domain.model.SceneState
import io.embodit.threeviews.domain.model.ViewFocus

/** Everything the projection box and the toolbar under it show. */
data class BoxUiState(
    /** The geometry of the current part. Null until the first drawing is ready; a failed part keeps the last one. */
    val scene: BoxSceneSpec? = null,
    /** True when the drawing of the selected part could not be computed. */
    val failed: Boolean = false,
    val focus: ViewFocus = ViewFocus.ALL,
    val unfolded: Boolean = false,
    val yawDegrees: Float = SceneState.DEFAULT_YAW,
    val showRays: Boolean = true,
    val showHidden: Boolean = true,
    /** The views whose projection rays are drawn. */
    val rays: Set<ViewKind> = emptySet(),
    /** The planes at full strength; the others step back while the user concentrates on one view. */
    val emphasised: Set<ViewKind> = ViewKind.entries.toSet(),
    val libraryOpen: Boolean = false,
    val sheetOpen: Boolean = false,
) {
    /** Rays connect the part to standing planes: a flat box has none. */
    val raysEnabled: Boolean get() = !unfolded

    /** Only the folded box turns, so only then is there an angle to reset. */
    val resetEnabled: Boolean get() = !unfolded
}

sealed interface BoxEvent {
    /** The segmented control: all, front, top or left. */
    data class Focus(val focus: ViewFocus) : BoxEvent

    data class SetUnfolded(val unfolded: Boolean) : BoxEvent

    data class SetRays(val show: Boolean) : BoxEvent

    data class SetHidden(val show: Boolean) : BoxEvent

    data object ResetView : BoxEvent

    /** A tap on one of the three projection planes. */
    data class PlaneTapped(val view: ViewKind) : BoxEvent

    /** A tap on the part itself. */
    data object PartTapped : BoxEvent

    /** A horizontal drag, already converted to degrees. */
    data class Turned(val deltaDegrees: Float) : BoxEvent
}
