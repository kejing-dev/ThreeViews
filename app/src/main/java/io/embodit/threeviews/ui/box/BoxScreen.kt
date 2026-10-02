package io.embodit.threeviews.ui.box

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import io.embodit.threeviews.platform.ThreeViewsGraph
import io.embodit.threeviews.ui.box.components.BoxToolbar
import io.embodit.threeviews.ui.box.components.ProjectionBoxView
import io.embodit.threeviews.ui.navigation.Containers

/**
 * The projection box: the main window of the app, a volume in Shared Space. It opens the part
 * library to its left at launch, opens the drawing sheet to its right on request, and takes both
 * along when it closes.
 */
@Composable
fun BoxScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val graph = remember(context) { ThreeViewsGraph.from(context) }
    val viewModel: BoxViewModel = viewModel(factory = BoxViewModel.factory(graph.repository, graph.sessionStore))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalSpatialNavigator.current

    DisposableEffect(navigator) {
        if (!graph.sessionStore.state.value.libraryOpen) navigator.openWindowContainer(Containers.LIBRARY, Containers.LIBRARY_TAG)
        onDispose {
            navigator.closeWindowContainer(Containers.LIBRARY, Containers.LIBRARY_TAG)
            navigator.closeWindowContainer(Containers.SHEET, Containers.SHEET_TAG)
        }
    }

    // A failed drawing is reported on the sheet, with Retry. Bring the sheet up if it is closed.
    val failed = state.failed
    LaunchedEffect(failed) {
        if (failed && !graph.sessionStore.state.value.sheetOpen) navigator.openWindowContainer(Containers.SHEET, Containers.SHEET_TAG)
    }

    BoxContent(
        state = state,
        onEvent = viewModel::onEvent,
        onLibraryToggle = { open ->
            if (open) navigator.openWindowContainer(Containers.LIBRARY, Containers.LIBRARY_TAG)
            else navigator.closeWindowContainer(Containers.LIBRARY, Containers.LIBRARY_TAG)
        },
        onSheetToggle = { open ->
            if (open) navigator.openWindowContainer(Containers.SHEET, Containers.SHEET_TAG)
            else navigator.closeWindowContainer(Containers.SHEET, Containers.SHEET_TAG)
        },
        modifier = modifier,
    )
}

@Composable
internal fun BoxContent(
    state: BoxUiState,
    onEvent: (BoxEvent) -> Unit,
    onLibraryToggle: (Boolean) -> Unit,
    onSheetToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ProjectionBoxView(state = state, onEvent = onEvent, modifier = modifier.fillMaxSize())
    BoxToolbar(state = state, onEvent = onEvent, onLibraryToggle = onLibraryToggle, onSheetToggle = onSheetToggle)
}
