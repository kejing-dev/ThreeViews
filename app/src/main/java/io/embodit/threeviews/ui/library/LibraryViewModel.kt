package io.embodit.threeviews.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.embodit.threeviews.data.repository.PartRepository
import io.embodit.threeviews.data.session.SceneSessionStore
import io.embodit.threeviews.domain.usecase.SceneControlUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The part library window: the list of parts and which one is in the projection box. */
class LibraryViewModel(
    repository: PartRepository,
    private val store: SceneSessionStore,
    private val control: SceneControlUseCase = SceneControlUseCase(),
) : ViewModel() {

    private val parts = repository.parts()
    private val partIds = parts.map { it.id }

    val state: StateFlow<LibraryUiState> =
        store.state.map { it.selectedPartId }.distinctUntilChanged()
            .map { LibraryUiState(parts, it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState(parts, store.state.value.selectedPartId))

    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.SelectPart -> store.update { control.selectPart(it, event.partId, partIds) }
            LibraryEvent.Opened -> store.update { control.setLibraryOpen(it, true) }
            LibraryEvent.Closed -> store.update { control.setLibraryOpen(it, false) }
        }
    }

    companion object {
        fun factory(repository: PartRepository, store: SceneSessionStore): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(repository, store) }
        }
    }
}
