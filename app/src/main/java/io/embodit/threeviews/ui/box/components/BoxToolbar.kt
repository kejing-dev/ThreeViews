package io.embodit.threeviews.ui.box.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.ToggleButton
import com.pico.spatial.ui.design.ToggleButtonDefaults
import com.pico.spatial.ui.design.VerticalDivider
import com.pico.spatial.ui.design.windows.Toolbar
import io.embodit.threeviews.R
import io.embodit.threeviews.domain.model.ViewFocus
import io.embodit.threeviews.ui.box.BoxEvent
import io.embodit.threeviews.ui.box.BoxUiState
import io.embodit.threeviews.ui.components.BilingualLabel

/**
 * The window-level toolbar under the projection box: which view to concentrate on, unfold, the line
 * options of the box, reset, and the switches of the two 2D windows. It is a sibling of the 3D
 * content; the system owns its position.
 */
@Composable
internal fun BoxToolbar(
    state: BoxUiState,
    onEvent: (BoxEvent) -> Unit,
    onLibraryToggle: (Boolean) -> Unit,
    onSheetToggle: (Boolean) -> Unit,
) {
    Toolbar {
        SegmentControl(modifier = Modifier.width(FocusControlWidth).height(56.dp)) {
            ViewFocus.entries.forEach { focus ->
                val (chinese, english) = when (focus) {
                    ViewFocus.ALL -> R.string.focus_all to R.string.focus_all_en
                    ViewFocus.FRONT -> R.string.focus_front to R.string.focus_front_en
                    ViewFocus.TOP -> R.string.focus_top to R.string.focus_top_en
                    ViewFocus.LEFT -> R.string.focus_left to R.string.focus_left_en
                }
                SegmentItem(
                    selected = state.focus == focus,
                    onClick = { onEvent(BoxEvent.Focus(focus)) },
                    modifier = Modifier.weight(1f),
                    title = { BilingualLabel(chinese, english) },
                )
            }
        }
        GroupDivider()
        OptionToggle(R.string.unfold, R.string.unfold_en, state.unfolded) { onEvent(BoxEvent.SetUnfolded(it)) }
        GroupDivider()
        OptionToggle(R.string.rays, R.string.rays_en, state.showRays, enabled = state.raysEnabled) { onEvent(BoxEvent.SetRays(it)) }
        OptionToggle(R.string.hidden_lines, R.string.hidden_lines_en, state.showHidden) { onEvent(BoxEvent.SetHidden(it)) }
        GroupDivider()
        Button(
            onClick = { onEvent(BoxEvent.ResetView) },
            enabled = state.resetEnabled,
            size = ButtonDefaults.buttonSize(width = ControlWidth, height = ControlHeight),
            contentPadding = ControlPadding,
            shape = ControlShape,
        ) {
            BilingualLabel(R.string.reset_view, R.string.reset_view_en)
        }
        GroupDivider()
        OptionToggle(R.string.window_library, R.string.window_library_en, state.libraryOpen, onCheckedChange = onLibraryToggle)
        OptionToggle(R.string.window_sheet, R.string.window_sheet_en, state.sheetOpen, onCheckedChange = onSheetToggle)
    }
}

@Composable
private fun OptionToggle(
    chinese: Int,
    english: Int,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.padding(horizontal = 3.dp),
        colors = ToggleButtonDefaults.toggleButtonColors(
            checkedContainerColor = PicoTheme.colorScheme.interaction,
            checkedContentColor = PicoTheme.colorScheme.labelPrimaryLight,
        ),
        size = ToggleButtonDefaults.toggleButtonSize(width = ControlWidth, height = ControlHeight),
        enabled = enabled,
        contentPadding = ControlPadding,
        shape = ControlShape,
    ) {
        BilingualLabel(chinese, english)
    }
}

@Composable
private fun GroupDivider() {
    VerticalDivider(modifier = Modifier.padding(horizontal = 8.dp).height(32.dp))
}

/** Four segments of 60 dp plus the control's own padding and gaps. */
private val FocusControlWidth = 264.dp

/**
 * The toolbar hangs under a volume 0.8 m wide, so its ten controls are kept compact: 76 dp wide,
 * 56 dp high for the two-line labels.
 */
private val ControlWidth = 76.dp
private val ControlHeight = 56.dp
private val ControlPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
private val ControlShape = RoundedCornerShape(28.dp)
