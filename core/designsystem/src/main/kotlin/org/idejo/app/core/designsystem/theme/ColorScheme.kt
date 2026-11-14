// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The forum's colours as a Material 3 scheme (TH-02), as idejo maps them for the web:
 * - primary, secondary and tertiary as they are, with white or `#111111` on them, whichever
 *   reads better;
 * - background the page's (light or dark); on-background the other background, unless it
 *   lacks contrast;
 * - surface a little lighter than the page (cards), surface-container a little darker;
 * - every text colour at least 4.5:1, outlines 3:1 (AX-01).
 *
 * Long only because it names every Material role, one per line.
 */
@Suppress("LongMethod")
fun ForumTheme.colorScheme(dark: Boolean): ColorScheme {
    val background =
        if (dark) {
            darkBackground.opaqueOver(
                Color.Black,
            )
        } else {
            lightBackground.opaqueOver(Color.White)
        }
    val other =
        if (dark) {
            lightBackground.opaqueOver(
                Color.White,
            )
        } else {
            darkBackground.opaqueOver(Color.Black)
        }
    val tones = Surfaces.of(background, dark)
    val surface = tones.surface
    val surfaces = listOf(background, surface, tones.highest)

    // The other background when it reads on the page and every surface, else the better of
    // white and #111111 there.
    val worst = { text: Color -> surfaces.minOf { contrastRatio(text, it) } }
    val onBackground =
        if (worst(other) >= TEXT_CONTRAST) other else listOf(Color.White, OnLight).maxBy(worst)

    val onSurfaceVariant = readableOn(mix(onBackground, background, SOFT_TEXT), surfaces)
    val error = if (dark) DARK_ERROR else LIGHT_ERROR
    val tonal = { color: Color -> mix(color.copy(alpha = 1f), background, CONTAINER) }

    // Every role is given, so the light builder serves both themes.
    return lightColorScheme(
        primary = primary,
        onPrimary = onColorFor(primary),
        primaryContainer = tonal(primary),
        onPrimaryContainer = onColorFor(tonal(primary)),
        inversePrimary = primary,
        secondary = secondary,
        onSecondary = onColorFor(secondary),
        secondaryContainer = tonal(secondary),
        onSecondaryContainer = onColorFor(tonal(secondary)),
        tertiary = tertiary,
        onTertiary = onColorFor(tertiary),
        tertiaryContainer = tonal(tertiary),
        onTertiaryContainer = onColorFor(tonal(tertiary)),
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onBackground,
        surfaceVariant = tones.highest,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = onBackground,
        inverseOnSurface = background,
        error = error,
        onError = onColorFor(error),
        errorContainer = tonal(error),
        onErrorContainer = onColorFor(tonal(error)),
        outline = readableOn(mix(onBackground, background, OUTLINE), surfaces, GRAPHIC_CONTRAST),
        outlineVariant = mix(onBackground, background, OUTLINE_VARIANT),
        scrim = Color.Black,
        surfaceBright = surface,
        surfaceDim = tones.high,
        surfaceContainerLowest = surface,
        surfaceContainerLow = tones.low,
        surfaceContainer = tones.container,
        surfaceContainerHigh = tones.high,
        surfaceContainerHighest = tones.highest,
    )
}

/** The page's surfaces: one a little lighter (cards), four steps darker (containers). */
private class Surfaces(
    val surface: Color,
    val low: Color,
    val container: Color,
    val high: Color,
    val highest: Color,
) {
    companion object {
        fun of(
            background: Color,
            dark: Boolean,
        ): Surfaces {
            val step = if (dark) DARK_DARKER else LIGHT_DARKER
            val darker = { steps: Int -> mix(background, Color.Black, step * steps) }
            return Surfaces(
                surface = mix(background, Color.White, if (dark) DARK_LIGHTER else LIGHT_LIGHTER),
                low = darker(1),
                container = darker(2),
                high = darker(3),
                highest = darker(4),
            )
        }
    }
}

private const val LIGHT_LIGHTER = 0.7f
private const val DARK_LIGHTER = 0.06f
private const val LIGHT_DARKER = 0.02f
private const val DARK_DARKER = 0.06f
private const val SOFT_TEXT = 0.3f
private const val OUTLINE = 0.5f
private const val OUTLINE_VARIANT = 0.82f
private const val CONTAINER = 0.7f
private val LIGHT_ERROR = Color(0xFFB3261E)
private val DARK_ERROR = Color(0xFFF2B8B5)
