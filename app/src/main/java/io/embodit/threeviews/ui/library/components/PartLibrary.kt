package io.embodit.threeviews.ui.library.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.DotBadge
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SideNavigation
import com.pico.spatial.ui.design.SideNavigationItem
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.haptic.controllerHapticFeedback
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import io.embodit.threeviews.R
import io.embodit.threeviews.domain.model.Difficulty
import io.embodit.threeviews.domain.model.PartSummary

/**
 * The part library, in Chinese and English: number, name, a few words on the structure, and how
 * hard the part is to read.
 */
@Composable
internal fun PartLibrary(
    parts: List<PartSummary>,
    selectedPartId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SideNavigation(
        modifier = modifier,
        header = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.library_title),
                        style = PicoTheme.typography.titleLarge,
                        color = PicoTheme.colorScheme.labelPrimary,
                    )
                    Text(
                        text = stringResource(R.string.library_title_en),
                        style = PicoTheme.typography.titleSmall,
                        color = PicoTheme.colorScheme.labelSecondary,
                    )
                }
                Text(
                    text = stringResource(R.string.library_count, parts.size),
                    style = PicoTheme.typography.bodySmall,
                    color = PicoTheme.colorScheme.labelSecondary,
                )
            }
        },
    ) {
        parts.forEach { part ->
            PartRow(part = part, selected = part.id == selectedPartId, onClick = { onSelect(part.id) })
        }
    }
}

@Composable
private fun PartRow(part: PartSummary, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val interaction = remember { MutableInteractionSource() }
    SideNavigationItem(
        selected = selected,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .spatialHoverEffect()
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .controllerHapticFeedback(interactionSource = interaction),
        shape = shape,
        leading = {
            Text(
                text = "%02d".format(part.number),
                style = PicoTheme.typography.labelMedium,
                color = PicoTheme.colorScheme.labelSecondary,
            )
        },
        trailing = { DifficultyTag(part.difficulty) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // design-style: inherited-content-color SideNavigationItem
                Text(
                    text = part.nameZh,
                    style = PicoTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = part.nameEn,
                    modifier = Modifier.weight(1f, fill = false),
                    style = PicoTheme.typography.bodySmall,
                    color = PicoTheme.colorScheme.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected) {
                    val current = stringResource(R.string.cd_selected_part)
                    DotBadge(
                        modifier = Modifier.size(8.dp).semantics { contentDescription = current },
                        color = PicoTheme.colorScheme.interaction,
                    )
                }
            }
            Text(
                text = part.summaryZh ?: stringResource(R.string.library_no_summary),
                style = PicoTheme.typography.bodySmall,
                color = PicoTheme.colorScheme.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = part.summaryEn ?: stringResource(R.string.library_no_summary_en),
                style = PicoTheme.typography.bodyTiny,
                color = PicoTheme.colorScheme.labelTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** How hard a part is to read, in both languages. */
@Composable
private fun DifficultyTag(difficulty: Difficulty?) {
    val (chinese, english) = when (difficulty) {
        Difficulty.EASY -> R.string.difficulty_easy to R.string.difficulty_easy_en
        Difficulty.MEDIUM -> R.string.difficulty_medium to R.string.difficulty_medium_en
        Difficulty.HARD -> R.string.difficulty_hard to R.string.difficulty_hard_en
        null -> R.string.difficulty_unknown to R.string.difficulty_unknown
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = stringResource(chinese),
            style = PicoTheme.typography.labelMedium,
            color = PicoTheme.colorScheme.labelSecondary,
            maxLines = 1,
        )
        if (difficulty != null) {
            Text(
                text = stringResource(english),
                style = PicoTheme.typography.bodyTiny,
                color = PicoTheme.colorScheme.labelTertiary,
                maxLines = 1,
            )
        }
    }
}
