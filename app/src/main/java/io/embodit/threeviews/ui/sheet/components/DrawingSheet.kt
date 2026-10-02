package io.embodit.threeviews.ui.sheet.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.DotBadge
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.haptic.controllerHapticFeedback
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import io.embodit.threeviews.R
import io.embodit.threeviews.domain.geometry.Polyline2
import io.embodit.threeviews.domain.geometry.ViewKind
import io.embodit.threeviews.domain.model.AlignmentRule
import io.embodit.threeviews.domain.model.DrawingStatus
import io.embodit.threeviews.domain.model.PartSummary
import io.embodit.threeviews.domain.model.SheetLayout
import io.embodit.threeviews.domain.model.ViewFocus
import io.embodit.threeviews.domain.usecase.LineDashUseCase
import io.embodit.threeviews.ui.components.BilingualLabel
import io.embodit.threeviews.ui.theme.ThreeViewsColors
import io.embodit.threeviews.ui.theme.fixed

/**
 * The drawing sheet: an opaque sheet of tracing paper inside the glass window, so the black line
 * work always has a light ground. It shows the three views in first-angle arrangement with the
 * projection axes, the 45 degree line, optional alignment guides and a title block.
 */
@Composable
internal fun DrawingSheet(
    drawing: DrawingStatus,
    part: PartSummary?,
    focus: ViewFocus,
    showHidden: Boolean,
    showGuides: Boolean,
    onViewTapped: (ViewKind) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = ThreeViewsColors.Ink.fixed()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ThreeViewsColors.Paper.fixed())
            .padding(20.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize().border(1.5.dp, ink)) {
            when (drawing) {
                is DrawingStatus.Ready -> {
                    Views(drawing, focus, showHidden, showGuides, onViewTapped)
                    TitleBlock(part = part, modifier = Modifier.align(Alignment.BottomEnd))
                }
                is DrawingStatus.Failed -> ComputeError(onRetry, Modifier.align(Alignment.Center))
                DrawingStatus.Loading -> Text(
                    text = stringResource(R.string.sheet_loading),
                    modifier = Modifier.align(Alignment.Center),
                    style = PicoTheme.typography.bodyMedium,
                    color = ThreeViewsColors.InkSoft.fixed(),
                )
            }
        }
    }
}

/** Maps sheet millimetres onto the dp grid of the drawing area. */
private class SheetTransform(val sheet: SheetLayout, val dpPerMm: Float, val left: Float, val top: Float) {
    fun x(mm: Double): Float = left + ((mm - sheet.paperLeft) * dpPerMm).toFloat()
    fun y(mm: Double): Float = top + ((mm - sheet.paperTop) * dpPerMm).toFloat()
    fun length(mm: Double): Float = (mm * dpPerMm).toFloat()

    companion object {
        /**
         * The largest drawing that fits the frame without running under the title block: either to
         * the left of it or above it, whichever gives the bigger scale.
         */
        fun fit(sheet: SheetLayout, width: Float, height: Float): SheetTransform {
            val paperW = (sheet.paperRight - sheet.paperLeft).toFloat()
            val paperH = (sheet.paperBottom - sheet.paperTop).toFloat()
            val pad = FramePadding.value
            fun scale(w: Float, h: Float) = minOf((w - 2 * pad) / paperW, (h - 2 * pad) / paperH)
            val beside = scale(width - TitleBlockWidth.value, height)
            val above = scale(width, height - TitleBlockHeight.value)
            val (areaW, areaH, s) = if (beside >= above) Triple(width - TitleBlockWidth.value, height, beside) else Triple(width, height - TitleBlockHeight.value, above)
            val dpPerMm = s.coerceAtLeast(0.5f)
            return SheetTransform(sheet, dpPerMm, (areaW - paperW * dpPerMm) / 2, (areaH - paperH * dpPerMm) / 2)
        }
    }
}

@Composable
private fun Views(
    ready: DrawingStatus.Ready,
    focus: ViewFocus,
    showHidden: Boolean,
    showGuides: Boolean,
    onViewTapped: (ViewKind) -> Unit,
) {
    val ink = ThreeViewsColors.Ink.fixed()
    val inkSoft = ThreeViewsColors.InkSoft.fixed()
    val centerColor = ThreeViewsColors.CenterLine.fixed()
    val guideColor = ThreeViewsColors.RayBlue.fixed()
    val accent = PicoTheme.colorScheme.interaction
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sheet = ready.sheet
        val t = SheetTransform.fit(sheet, maxWidth.value, maxHeight.value)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val px = density
            fun at(x: Double, y: Double) = Offset(t.x(x) * px, t.y(y) * px)
            focus.view?.let { focusQuadrant(it, sheet, t, ink.copy(alpha = FocusTintAlpha), accent) }
            val thin = (t.length(LineDashUseCase.THIN) * px).coerceIn(1.2f * px, 2f * px)
            val thick = thin * 2

            // Projection axes: where the three planes were hinged before they were laid flat.
            drawLine(inkSoft, at(sheet.paperLeft, sheet.origin.y), at(sheet.paperRight, sheet.origin.y), strokeWidth = px)
            drawLine(inkSoft, at(sheet.origin.x, sheet.paperTop), at(sheet.origin.x, sheet.paperBottom), strokeWidth = px)
            // The 45 degree line that carries widths from the top view into the left view.
            drawLine(guideColor.copy(alpha = 0.7f), at(sheet.miterStart.x, sheet.miterStart.y), at(sheet.miterEnd.x, sheet.miterEnd.y), strokeWidth = px)

            if (showGuides) {
                for (guide in sheet.guides) {
                    val path = Path()
                    guide.points.forEachIndexed { i, p -> at(p.x, p.y).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
                    drawPath(path, guideColor, style = Stroke(width = px))
                }
            }

            for (view in ViewKind.entries) {
                val placement = sheet.placement(view)
                val lines = ready.drawing.view(view)
                val alpha = if (focus == ViewFocus.ALL || focus.view == view) 1f else DimmedAlpha
                fun path(lineSet: List<Polyline2>): Path = Path().apply {
                    for (line in lineSet) {
                        line.points.forEachIndexed { i, p ->
                            val q = placement.toSheet(p).let { at(it.x, it.y) }
                            if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y)
                        }
                        if (line.closed) close()
                    }
                }
                fun dashes(pattern: DoubleArray) = PathEffect.dashPathEffect(FloatArray(pattern.size) { t.length(pattern[it]) * px })
                if (showHidden) {
                    drawPath(path(lines.hidden), ink.copy(alpha = alpha), style = Stroke(width = thin, pathEffect = dashes(LineDashUseCase.HIDDEN)))
                }
                drawPath(path(lines.center), centerColor.copy(alpha = alpha), style = Stroke(width = thin, pathEffect = dashes(LineDashUseCase.CENTER)))
                drawPath(path(lines.visible), ink.copy(alpha = alpha), style = Stroke(width = thick, cap = StrokeCap.Square, join = StrokeJoin.Miter))
            }
        }

        // One tap target per quadrant: tapping a view concentrates on it.
        for (view in ViewKind.entries) {
            val (x0, y0, x1, y1) = quadrant(view, sheet)
            val label = stringResource(R.string.cd_focus_view, stringResource(viewName(view)), stringResource(viewNameEnglish(view)))
            val interaction = remember(view) { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .offset((t.x(x0) + TapInset).dp, (t.y(y0) + TapInset).dp)
                    .size((t.x(x1) - t.x(x0) - 2 * TapInset).dp, (t.y(y1) - t.y(y0) - 2 * TapInset).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .spatialHoverEffect()
                    .clickable(interactionSource = interaction, indication = LocalIndication.current) { onViewTapped(view) }
                    .controllerHapticFeedback(interactionSource = interaction)
                    .semantics { contentDescription = label },
            )
        }

        // View names sit in the outer corner of each quadrant, as on a classroom sheet.
        ViewLabel(ViewKind.FRONT, focus, Modifier.offset((t.x(sheet.paperLeft) + 8).dp, (t.y(sheet.paperTop) + 4).dp))
        Box(
            modifier = Modifier.offset((t.x(sheet.paperRight) - ViewLabelWidth - 8).dp, (t.y(sheet.paperTop) + 4).dp).width(ViewLabelWidth.dp),
            contentAlignment = Alignment.TopEnd,
        ) { ViewLabel(ViewKind.LEFT, focus, Modifier) }
        ViewLabel(ViewKind.TOP, focus, Modifier.offset((t.x(sheet.paperLeft) + 8).dp, (t.y(sheet.paperBottom) - 24).dp))

        AxisLabel("X", Modifier.offset((t.x(sheet.paperLeft) + 2).dp, (t.y(sheet.origin.y) - 20).dp))
        AxisLabel("Z", Modifier.offset((t.x(sheet.origin.x) + 6).dp, (t.y(sheet.paperTop) - 2).dp))
        AxisLabel("O", Modifier.offset((t.x(sheet.origin.x) - 16).dp, (t.y(sheet.origin.y) + 2).dp))
        AxisLabel("Yw", Modifier.offset((t.x(sheet.paperRight) - 24).dp, (t.y(sheet.origin.y) - 20).dp))
        AxisLabel("Yh", Modifier.offset((t.x(sheet.origin.x) + 6).dp, (t.y(sheet.paperBottom) - 18).dp))

        if (showGuides) {
            // Each rule is named next to the gap its guides cross.
            RuleLabel(AlignmentRule.LENGTH, sheet, Modifier.offset((t.x(sheet.paperLeft) + 14).dp, (t.y(sheet.origin.y) + 8).dp))
            RuleLabel(AlignmentRule.HEIGHT, sheet, Modifier.offset((t.x(sheet.origin.x) + 24).dp, (t.y(sheet.paperTop) + 4).dp))
            RuleLabel(AlignmentRule.WIDTH, sheet, Modifier.offset((t.x(sheet.left.x)).dp, (t.y(sheet.top.bottom) + 4).dp))
        }
    }
}

/** Tints and outlines the quadrant of the view the user is concentrating on. */
private fun DrawScope.focusQuadrant(view: ViewKind, sheet: SheetLayout, t: SheetTransform, tint: Color, outline: Color) {
    val px = density
    val (x0, y0, x1, y1) = quadrant(view, sheet)
    val topLeft = Offset((t.x(x0) + TapInset) * px, (t.y(y0) + TapInset) * px)
    val size = Size((t.x(x1) - t.x(x0) - 2 * TapInset) * px, (t.y(y1) - t.y(y0) - 2 * TapInset) * px)
    val radius = CornerRadius(12 * px, 12 * px)
    drawRoundRect(color = tint, topLeft = topLeft, size = size, cornerRadius = radius)
    drawRoundRect(color = outline, topLeft = topLeft, size = size, cornerRadius = radius, style = Stroke(width = 2 * px))
}

/** The part of the sheet that belongs to a view: left, top, right, bottom in sheet millimetres. */
private fun quadrant(view: ViewKind, sheet: SheetLayout): List<Double> = when (view) {
    ViewKind.FRONT -> listOf(sheet.paperLeft, sheet.paperTop, sheet.origin.x, sheet.origin.y)
    ViewKind.TOP -> listOf(sheet.paperLeft, sheet.origin.y, sheet.origin.x, sheet.paperBottom)
    ViewKind.LEFT -> listOf(sheet.origin.x, sheet.paperTop, sheet.paperRight, sheet.origin.y)
}

private fun viewName(view: ViewKind): Int = when (view) {
    ViewKind.FRONT -> R.string.view_front
    ViewKind.TOP -> R.string.view_top
    ViewKind.LEFT -> R.string.view_left
}

private fun viewNameEnglish(view: ViewKind): Int = when (view) {
    ViewKind.FRONT -> R.string.view_front_en
    ViewKind.TOP -> R.string.view_top_en
    ViewKind.LEFT -> R.string.view_left_en
}

@Composable
private fun ViewLabel(view: ViewKind, focus: ViewFocus, modifier: Modifier) {
    val focused = focus.view == view
    Row(
        modifier = modifier.alpha(if (focus == ViewFocus.ALL || focused) 1f else DimmedAlpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (focused) DotBadge(modifier = Modifier.size(8.dp), color = PicoTheme.colorScheme.interaction)
        Text(
            text = stringResource(viewName(view)),
            style = PicoTheme.typography.labelMedium,
            color = if (focused) ThreeViewsColors.Ink.fixed() else ThreeViewsColors.InkSoft.fixed(),
            maxLines = 1,
        )
        Text(
            text = stringResource(viewNameEnglish(view)),
            style = PicoTheme.typography.bodyTiny,
            color = ThreeViewsColors.InkSoft.fixed(),
            maxLines = 1,
        )
    }
}

@Composable
private fun AxisLabel(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = PicoTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic,
        color = ThreeViewsColors.InkSoft.fixed(),
        maxLines = 1,
    )
}

/** The name of an alignment rule with a check mark when the drawing satisfies it. */
@Composable
private fun RuleLabel(rule: AlignmentRule, sheet: SheetLayout, modifier: Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (sheet.holds[rule] == true) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = PicoTheme.colorScheme.passable,
            )
        }
        Text(
            text = stringResource(
                when (rule) {
                    AlignmentRule.LENGTH -> R.string.rule_length
                    AlignmentRule.HEIGHT -> R.string.rule_height
                    AlignmentRule.WIDTH -> R.string.rule_width
                },
            ),
            style = PicoTheme.typography.labelSmall,
            color = ThreeViewsColors.RayBlue.fixed(),
            maxLines = 1,
        )
    }
}

/** Shown on the paper in place of the views when the drawing could not be computed. */
@Composable
private fun ComputeError(onRetry: () -> Unit, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(
            painter = painterResource(R.drawable.ic_warning),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = PicoTheme.colorScheme.error,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.sheet_failed),
                style = PicoTheme.typography.titleSmall,
                color = ThreeViewsColors.Ink.fixed(),
            )
            Text(
                text = stringResource(R.string.sheet_failed_en),
                style = PicoTheme.typography.bodySmall,
                color = ThreeViewsColors.InkSoft.fixed(),
            )
        }
        Button(
            onClick = onRetry,
            size = ButtonDefaults.Max,
            colors = ButtonDefaults.buttonColors(
                containerColor = PicoTheme.colorScheme.interaction,
                contentColor = PicoTheme.colorScheme.labelPrimaryLight,
            ),
        ) {
            BilingualLabel(R.string.retry, R.string.retry_en)
        }
    }
}

internal val TitleBlockWidth: Dp = 380.dp
internal val TitleBlockHeight: Dp = 124.dp
private val FramePadding: Dp = 16.dp
private const val FocusTintAlpha = 0.06f
private const val TapInset = 4f
private const val ViewLabelWidth = 200f
private const val DimmedAlpha = 0.3f
