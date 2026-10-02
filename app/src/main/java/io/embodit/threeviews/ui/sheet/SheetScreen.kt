package io.embodit.threeviews.ui.sheet

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.embodit.threeviews.platform.ThreeViewsGraph
import io.embodit.threeviews.ui.sheet.components.DrawingSheet
import io.embodit.threeviews.ui.sheet.components.SheetToolbar

/**
 * The drawing sheet window: the three views of the part in the projection box, laid out as a
 * first-angle drawing. It opens to the right of the box from the box's toolbar.
 */
@Composable
fun SheetScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val graph = remember(context) { ThreeViewsGraph.from(context) }
    val viewModel: SheetViewModel = viewModel(factory = SheetViewModel.factory(graph.repository, graph.sessionStore))
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The toolbar under the projection box shows whether this window is open.
    DisposableEffect(viewModel) {
        viewModel.onEvent(SheetEvent.Opened)
        onDispose { viewModel.onEvent(SheetEvent.Closed) }
    }

    SheetContent(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
internal fun SheetContent(
    state: SheetUiState,
    onEvent: (SheetEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    DrawingSheet(
        drawing = state.drawing,
        part = state.part,
        focus = state.focus,
        showHidden = state.showHidden,
        showGuides = state.showGuides,
        onViewTapped = { onEvent(SheetEvent.ViewTapped(it)) },
        onRetry = { onEvent(SheetEvent.Retry) },
        modifier = modifier.fillMaxSize().padding(SheetMargin),
    )
    SheetToolbar(state = state, onEvent = onEvent)
}

/** PICO padding token Large: the same margin on every side of the sheet. */
private val SheetMargin = 32.dp
