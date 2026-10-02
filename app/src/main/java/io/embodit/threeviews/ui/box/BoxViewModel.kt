package io.embodit.threeviews.ui.box

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.embodit.threeviews.data.repository.PartRepository
import io.embodit.threeviews.data.session.SceneSessionStore
import io.embodit.threeviews.domain.geometry.ViewKind
import io.embodit.threeviews.domain.model.BoxSceneSpec
import io.embodit.threeviews.domain.model.DrawingStatus
import io.embodit.threeviews.domain.model.SceneState
import io.embodit.threeviews.domain.model.ViewFocus
import io.embodit.threeviews.domain.usecase.BoxSceneUseCase
import io.embodit.threeviews.domain.usecase.PrepareDrawingUseCase
import io.embodit.threeviews.domain.usecase.SceneControlUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

/**
 * The projection box and its toolbar: the main window of the app. It follows the scene options in
 * the shared [SceneSessionStore] and turns the drawing of the selected part into box geometry off
 * the main thread.
 */
class BoxViewModel(
    repository: PartRepository,
    private val store: SceneSessionStore,
    prepare: PrepareDrawingUseCase = PrepareDrawingUseCase(repository),
    private val sceneBuilder: BoxSceneUseCase = BoxSceneUseCase(),
    private val control: SceneControlUseCase = SceneControlUseCase(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /** The last part that computed successfully, and whether the part asked for last failed. */
    private data class Shown(val spec: BoxSceneSpec? = null, val failed: Boolean = false)

    private val shown: Flow<Shown> =
        prepare.observe(store.state)
            .scan(Shown()) { last, status ->
                when (status) {
                    is DrawingStatus.Ready -> Shown(sceneBuilder.build(status), failed = false)
                    // A loading or failed part leaves the previous one in the box.
                    is DrawingStatus.Failed -> last.copy(failed = true)
                    DrawingStatus.Loading -> last.copy(failed = false)
                }
            }
            .flowOn(dispatcher)

    val state: StateFlow<BoxUiState> =
        combine(store.state, shown) { scene, box -> reduce(scene, box) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, reduce(store.state.value, Shown()))

    fun onEvent(event: BoxEvent) {
        when (event) {
            is BoxEvent.Focus -> store.update { control.focus(it, event.focus) }
            is BoxEvent.SetUnfolded -> store.update { control.setUnfolded(it, event.unfolded) }
            is BoxEvent.SetRays -> store.update { control.setRays(it, event.show) }
            is BoxEvent.SetHidden -> store.update { control.setHidden(it, event.show) }
            BoxEvent.ResetView -> store.update(control::reset)
            is BoxEvent.PlaneTapped -> store.update { control.toggleFocus(it, event.view) }
            BoxEvent.PartTapped -> store.update { control.focus(it, ViewFocus.ALL) }
            is BoxEvent.Turned -> store.update { control.turn(it, event.deltaDegrees) }
        }
    }

    private fun reduce(scene: SceneState, box: Shown): BoxUiState =
        BoxUiState(
            scene = box.spec,
            failed = box.failed,
            focus = scene.focus,
            unfolded = scene.unfolded,
            yawDegrees = scene.yawDegrees,
            showRays = scene.showRays,
            showHidden = scene.showHidden,
            rays = ViewKind.entries.filter { control.raysVisible(scene, it) }.toSet(),
            emphasised = ViewKind.entries.filter { control.planeEmphasised(scene, it) }.toSet(),
            libraryOpen = scene.libraryOpen,
            sheetOpen = scene.sheetOpen,
        )

    companion object {
        fun factory(repository: PartRepository, store: SceneSessionStore): ViewModelProvider.Factory = viewModelFactory {
            initializer { BoxViewModel(repository, store) }
        }
    }
}
