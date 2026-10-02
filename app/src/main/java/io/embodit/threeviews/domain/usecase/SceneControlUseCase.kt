package io.embodit.threeviews.domain.usecase

import io.embodit.threeviews.domain.geometry.ViewKind
import io.embodit.threeviews.domain.model.SceneState
import io.embodit.threeviews.domain.model.ViewFocus

/**
 * The rules both surfaces follow when the user changes the scene. Pure reducers on [SceneState],
 * so the drawing window and the projection box can never disagree about what a gesture means.
 */
class SceneControlUseCase {

    /** Picks another part. An unknown id is ignored. The view options stay as the user set them. */
    fun selectPart(state: SceneState, partId: String, knownIds: Collection<String>): SceneState =
        if (partId in knownIds) state.copy(selectedPartId = partId) else state

    fun focus(state: SceneState, focus: ViewFocus): SceneState = state.copy(focus = focus)

    /** Tapping a view or its plane concentrates on it; tapping it again shows all three. */
    fun toggleFocus(state: SceneState, view: ViewKind): SceneState =
        state.copy(focus = if (state.focus.view == view) ViewFocus.ALL else ViewFocus.of(view))

    fun setUnfolded(state: SceneState, unfolded: Boolean): SceneState = state.copy(unfolded = unfolded)

    fun setRays(state: SceneState, show: Boolean): SceneState = state.copy(showRays = show)

    fun setHidden(state: SceneState, show: Boolean): SceneState = state.copy(showHidden = show)

    fun setGuides(state: SceneState, show: Boolean): SceneState = state.copy(showGuides = show)

    /** Turns the box about the vertical axis. A flat sheet does not turn. */
    fun turn(state: SceneState, deltaDegrees: Float): SceneState =
        if (state.unfolded) state
        else state.copy(yawDegrees = (state.yawDegrees + deltaDegrees).coerceIn(SceneState.MIN_YAW, SceneState.MAX_YAW))

    /** Turns the folded box back to the three-quarter view of the textbook figure. */
    fun reset(state: SceneState): SceneState =
        if (state.unfolded) state else state.copy(yawDegrees = SceneState.DEFAULT_YAW)

    /** Asks for the drawing of the selected part again after a failure. */
    fun retry(state: SceneState): SceneState = state.copy(attempt = state.attempt + 1)

    fun setLibraryOpen(state: SceneState, open: Boolean): SceneState = state.copy(libraryOpen = open)

    fun setSheetOpen(state: SceneState, open: Boolean): SceneState = state.copy(sheetOpen = open)

    /** Rays only make sense while the planes stand around the part. */
    fun raysVisible(state: SceneState, view: ViewKind): Boolean =
        state.showRays && !state.unfolded && (state.focus == ViewFocus.ALL || state.focus.view == view)

    /** A plane steps back when the user concentrates on another view. */
    fun planeEmphasised(state: SceneState, view: ViewKind): Boolean =
        state.focus == ViewFocus.ALL || state.focus.view == view
}
