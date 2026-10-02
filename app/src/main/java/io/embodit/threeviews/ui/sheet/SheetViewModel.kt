package io.embodit.threeviews.ui.sheet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.embodit.threeviews.data.repository.PartRepository
import io.embodit.threeviews.data.session.SceneSessionStore
import io.embodit.threeviews.domain.model.DrawingStatus
import io.embodit.threeviews.domain.model.SceneState
import io.embodit.threeviews.domain.usecase.PrepareDrawingUseCase
import io.embodit.threeviews.domain.usecase.SceneControlUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The drawing sheet window. Scene options live in the shared [SceneSessionStore], so the sheet
 * follows the part picked in the library and the view focused in the projection box; this
 * ViewModel adds the computed drawing.
 */
class SheetViewModel(
    repository: PartRepository,
    private val store: SceneSessionStore,
    prepare: PrepareDrawingUseCase = PrepareDrawingUseCase(repository),
    private val control: SceneControlUseCase = SceneControlUseCase(),
) : ViewModel() {

    private val parts = repository.parts()

    private val drawing: Flow<DrawingStatus> = prepare.observe(store.state)

    val state: StateFlow<SheetUiState> =
        combine(store.state, drawing) { scene, status -> reduce(scene, status) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, reduce(store.state.value, DrawingStatus.Loading))

    fun onEvent(event: SheetEvent) {
        when (event) {
            is SheetEvent.ViewTapped -> store.update { control.toggleFocus(it, event.view) }
            is SheetEvent.SetGuides -> store.update { control.setGuides(it, event.show) }
            SheetEvent.Retry -> store.update(control::retry)
            SheetEvent.Opened -> store.update { control.setSheetOpen(it, true) }
            SheetEvent.Closed -> store.update { control.setSheetOpen(it, false) }
        }
    }

    private fun reduce(scene: SceneState, status: DrawingStatus): SheetUiState {
        // A result that belongs to the previously selected part is still on its way out.
        val current = when (status) {
            is DrawingStatus.Ready -> if (status.drawing.part.id == scene.selectedPartId) status else DrawingStatus.Loading
            is DrawingStatus.Failed -> if (status.partId == scene.selectedPartId) status else DrawingStatus.Loading
            DrawingStatus.Loading -> status
        }
        return SheetUiState(
            part = parts.firstOrNull { it.id == scene.selectedPartId },
            drawing = current,
            focus = scene.focus,
            showHidden = scene.showHidden,
            showGuides = scene.showGuides,
        )
    }

    companion object {
        fun factory(repository: PartRepository, store: SceneSessionStore): ViewModelProvider.Factory = viewModelFactory {
            initializer { SheetViewModel(repository, store) }
        }
    }
}
