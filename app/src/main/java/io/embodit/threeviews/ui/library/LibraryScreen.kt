package io.embodit.threeviews.ui.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.embodit.threeviews.platform.ThreeViewsGraph
import io.embodit.threeviews.ui.library.components.PartLibrary

/** The part library window: a narrow 2D window to the left of the projection box. */
@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val graph = remember(context) { ThreeViewsGraph.from(context) }
    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(graph.repository, graph.sessionStore))
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The toolbar under the projection box shows whether this window is open.
    DisposableEffect(viewModel) {
        viewModel.onEvent(LibraryEvent.Opened)
        onDispose { viewModel.onEvent(LibraryEvent.Closed) }
    }

    LibraryContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
internal fun LibraryContent(
    state: LibraryUiState,
    onEvent: (LibraryEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    PartLibrary(
        parts = state.parts,
        selectedPartId = state.selectedPartId,
        onSelect = { onEvent(LibraryEvent.SelectPart(it)) },
        modifier = modifier.fillMaxSize(),
    )
}
