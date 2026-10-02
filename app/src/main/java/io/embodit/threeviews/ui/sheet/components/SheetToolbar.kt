package io.embodit.threeviews.ui.sheet.components

import androidx.compose.runtime.Composable
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.ToggleButton
import com.pico.spatial.ui.design.ToggleButtonDefaults
import com.pico.spatial.ui.design.windows.Toolbar
import io.embodit.threeviews.R
import io.embodit.threeviews.ui.components.BilingualLabel
import io.embodit.threeviews.ui.sheet.SheetEvent
import io.embodit.threeviews.ui.sheet.SheetUiState

/**
 * The window-level toolbar of the sheet. Alignment guides exist only on paper, so their switch
 * lives here and not under the projection box.
 */
@Composable
internal fun SheetToolbar(state: SheetUiState, onEvent: (SheetEvent) -> Unit) {
    Toolbar {
        ToggleButton(
            checked = state.showGuides,
            onCheckedChange = { onEvent(SheetEvent.SetGuides(it)) },
            colors = ToggleButtonDefaults.toggleButtonColors(
                checkedContainerColor = PicoTheme.colorScheme.interaction,
                checkedContentColor = PicoTheme.colorScheme.labelPrimaryLight,
            ),
            size = ToggleButtonDefaults.Max,
            enabled = state.guidesEnabled,
        ) {
            BilingualLabel(R.string.guides, R.string.guides_en)
        }
    }
}
