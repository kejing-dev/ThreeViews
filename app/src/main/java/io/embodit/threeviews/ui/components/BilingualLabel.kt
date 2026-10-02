package io.embodit.threeviews.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text

/**
 * The label of a button, toggle or segment in both languages: Chinese on top, a smaller English
 * caption under it. It takes its color from the SpatialUI component it sits in, so it follows that
 * component's checked, pressed and disabled states.
 */
@Composable
fun BilingualLabel(chinese: Int, english: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // design-style: inherited-content-color Button / ToggleButton / SegmentItem
        Text(text = stringResource(chinese), style = PicoTheme.typography.labelMedium, maxLines = 1)
        // design-style: inherited-content-color Button / ToggleButton / SegmentItem
        Text(
            text = stringResource(english),
            modifier = Modifier.alpha(CaptionAlpha),
            style = PicoTheme.typography.bodyTiny,
            maxLines = 1,
        )
    }
}

private const val CaptionAlpha = 0.8f
