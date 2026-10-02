package io.embodit.threeviews.ui.sheet.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import io.embodit.threeviews.R
import io.embodit.threeviews.domain.model.PartSummary
import io.embodit.threeviews.ui.theme.ThreeViewsColors
import io.embodit.threeviews.ui.theme.fixed

/**
 * The title block in the lower right corner of the frame, in Chinese and English: the name of the
 * part, the first-angle projection symbol, scale, drawing number and unit.
 */
@Composable
internal fun TitleBlock(part: PartSummary?, modifier: Modifier = Modifier) {
    val number = part?.number
    val ink = ThreeViewsColors.Ink.fixed()
    Row(modifier = modifier.size(TitleBlockWidth, TitleBlockHeight).border(1.5.dp, ink)) {
        Column(modifier = Modifier.weight(1.65f).fillMaxHeight()) {
            Box(modifier = Modifier.weight(1.7f).fillMaxWidth().border(0.5.dp, ink).padding(horizontal = 8.dp, vertical = 4.dp)) {
                CellCaption(R.string.title_name, Modifier.align(Alignment.TopStart))
                Column(modifier = Modifier.align(Alignment.Center).padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = part?.nameZh.orEmpty(),
                        style = PicoTheme.typography.titleMedium,
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = part?.nameEn.orEmpty(),
                        style = PicoTheme.typography.bodyTiny,
                        color = ThreeViewsColors.InkSoft.fixed(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth().border(0.5.dp, ink).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FirstAngleSymbol(Modifier.size(width = 76.dp, height = 28.dp))
                Column {
                    Text(
                        text = stringResource(R.string.title_first_angle),
                        style = PicoTheme.typography.labelSmall,
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.title_first_angle_en),
                        style = PicoTheme.typography.bodyTiny,
                        color = ThreeViewsColors.InkSoft.fixed(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            ValueCell(R.string.title_scale, stringResource(R.string.title_scale_value), Modifier.weight(1f))
            ValueCell(
                R.string.title_number,
                number?.let { stringResource(R.string.title_number_value, it) } ?: stringResource(R.string.difficulty_unknown),
                Modifier.weight(1f),
            )
            ValueCell(R.string.title_unit, stringResource(R.string.title_unit_value), Modifier.weight(1f))
        }
    }
}

@Composable
private fun CellCaption(label: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(label),
        modifier = modifier,
        style = PicoTheme.typography.bodyTiny,
        color = ThreeViewsColors.InkSoft.fixed(),
        maxLines = 1,
    )
}

@Composable
private fun ValueCell(label: Int, value: String, modifier: Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().border(0.5.dp, ThreeViewsColors.Ink.fixed()).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CellCaption(label)
        Text(
            text = value,
            style = PicoTheme.typography.labelSmall,
            color = ThreeViewsColors.Ink.fixed(),
            maxLines = 1,
        )
    }
}

/**
 * The first-angle projection symbol of ISO 5456-2 / GB/T 14692: a truncated cone seen from the
 * side, and to its right what you see looking at its small end, two concentric circles.
 */
@Composable
private fun FirstAngleSymbol(modifier: Modifier) {
    val ink = ThreeViewsColors.Ink.fixed()
    val center = ThreeViewsColors.CenterLine.fixed()
    Canvas(modifier = modifier) {
        val px = density
        val mid = size.height / 2
        val big = size.height * 0.42f
        val small = big * 0.55f
        val coneLeft = 3 * px
        val coneRight = size.width * 0.48f
        val circleX = size.width * 0.78f
        val chain = PathEffect.dashPathEffect(floatArrayOf(7 * px, 2 * px, 1.5f * px, 2 * px))
        // side view: small end on the left, large end on the right
        val cone = Path().apply {
            moveTo(coneLeft, mid - small); lineTo(coneRight, mid - big); lineTo(coneRight, mid + big); lineTo(coneLeft, mid + small); close()
        }
        drawPath(cone, ink, style = Stroke(width = 1.2f * px))
        drawLine(center, Offset(0f, mid), Offset(coneRight + 4 * px, mid), strokeWidth = 0.8f * px, pathEffect = chain)
        // end view, placed on the far side as first-angle projection does
        drawCircle(ink, radius = big, center = Offset(circleX, mid), style = Stroke(width = 1.2f * px))
        drawCircle(ink, radius = small, center = Offset(circleX, mid), style = Stroke(width = 1.2f * px))
        drawLine(center, Offset(circleX - big - 3 * px, mid), Offset(circleX + big + 3 * px, mid), strokeWidth = 0.8f * px, pathEffect = chain)
        drawLine(center, Offset(circleX, mid - big - 2 * px), Offset(circleX, mid + big + 2 * px), strokeWidth = 0.8f * px, pathEffect = chain)
    }
}
