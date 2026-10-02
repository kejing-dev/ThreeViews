package io.embodit.threeviews.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.systemColorScheme

/**
 * The app's PicoTheme: the system Vibrant scheme with the design's semantic colors swapped in, plus
 * the full PicoTheme type scale rendered in Noto Sans. Every one of the 16 roles is assigned
 * explicitly so the final theme contract is visible here rather than implied by defaults.
 */
@Composable
fun ThreeViewsTheme(content: @Composable () -> Unit) {
    val system = systemColorScheme(LocalContext.current)
    val scheme =
        system.copy(
            fillPrimary = system.fillPrimary,
            fillSecondary = system.fillSecondary,
            fillTertiary = system.fillTertiary,
            fillLight = system.fillLight,
            labelPrimaryLight = ThreeViewsColors.OnInteraction,
            labelPrimary = system.labelPrimary,
            labelSecondary = system.labelSecondary,
            labelTertiary = system.labelTertiary,
            labelQuaternary = system.labelQuaternary,
            lightenHover = system.lightenHover,
            lightenPressed = system.lightenPressed,
            error = ThreeViewsColors.Error,
            alert = ThreeViewsColors.Alert,
            passable = ThreeViewsColors.Passable,
            interaction = ThreeViewsColors.Interaction,
            dividerLine = system.dividerLine,
        )
    PicoTheme(
        colorScheme = scheme,
        typography = PicoTheme.typography.withNotoSans(),
        content = content,
    )
}
