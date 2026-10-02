package io.embodit.threeviews.data.session

import io.embodit.threeviews.domain.model.SceneState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/** Process-wide scene state shared by the drawing window and the projection box. */
class SceneSessionStore(initial: SceneState = SceneState()) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<SceneState> = _state.asStateFlow()

    fun update(transform: (SceneState) -> SceneState): SceneState = _state.updateAndGet(transform)
}
