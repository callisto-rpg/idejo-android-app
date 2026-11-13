// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * A forum's look: its colours as a Material 3 scheme (TH-02) with idejo's extras, Fraunces
 * and Rubik (TH-04), edge-to-edge system bars (TH-09). A change of forum or theme
 * cross-fades the colours in 300 ms, at once with reduced motion (TH-07). No dynamic colour.
 */
@Composable
fun IdejoTheme(
    forumTheme: ForumTheme = ForumTheme.Default,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val target = remember(forumTheme, darkTheme) { forumTheme.colorScheme(darkTheme) }
    val scheme = animateColorScheme(target, themeFadeMillis(reducedMotion))
    val extras = remember(scheme, darkTheme) { idejoColors(scheme, darkTheme) }
    SystemBarsEffect(scheme.background)
    CompositionLocalProvider(
        LocalIdejoColors provides extras,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(colorScheme = scheme, typography = IdejoTypography, content = content)
    }
}

/** The theme's values in components: `IdejoTheme.colors.dot(…)`, `IdejoTheme.typography`. */
object IdejoTheme {
    val colors: IdejoColors
        @Composable @ReadOnlyComposable
        get() = LocalIdejoColors.current

    val typography: Typography
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography

    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalReducedMotion.current
}

@Composable
private fun animateColorScheme(
    target: ColorScheme,
    millis: Int,
): ColorScheme {
    @Composable
    fun animate(color: Color): Color =
        animateColorAsState(color, tween(durationMillis = millis), label = "theme").value

    return target.copy(
        primary = animate(target.primary),
        onPrimary = animate(target.onPrimary),
        primaryContainer = animate(target.primaryContainer),
        onPrimaryContainer = animate(target.onPrimaryContainer),
        inversePrimary = animate(target.inversePrimary),
        secondary = animate(target.secondary),
        onSecondary = animate(target.onSecondary),
        secondaryContainer = animate(target.secondaryContainer),
        onSecondaryContainer = animate(target.onSecondaryContainer),
        tertiary = animate(target.tertiary),
        onTertiary = animate(target.onTertiary),
        tertiaryContainer = animate(target.tertiaryContainer),
        onTertiaryContainer = animate(target.onTertiaryContainer),
        background = animate(target.background),
        onBackground = animate(target.onBackground),
        surface = animate(target.surface),
        onSurface = animate(target.onSurface),
        surfaceVariant = animate(target.surfaceVariant),
        onSurfaceVariant = animate(target.onSurfaceVariant),
        surfaceTint = animate(target.surfaceTint),
        inverseSurface = animate(target.inverseSurface),
        inverseOnSurface = animate(target.inverseOnSurface),
        outline = animate(target.outline),
        outlineVariant = animate(target.outlineVariant),
        surfaceBright = animate(target.surfaceBright),
        surfaceDim = animate(target.surfaceDim),
        surfaceContainer = animate(target.surfaceContainer),
        surfaceContainerHigh = animate(target.surfaceContainerHigh),
        surfaceContainerHighest = animate(target.surfaceContainerHighest),
        surfaceContainerLow = animate(target.surfaceContainerLow),
        surfaceContainerLowest = animate(target.surfaceContainerLowest),
    )
}
