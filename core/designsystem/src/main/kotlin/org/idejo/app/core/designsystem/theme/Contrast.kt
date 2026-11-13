// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** Text on a colour: white or this near-black, whichever reads better (TH-02). */
val OnLight = Color(0xFF111111)

/** WCAG 2.2 AA for normal text (AX-01). */
const val TEXT_CONTRAST = 4.5

/** WCAG 2.2 AA for icons, dots and outlines (AX-01). */
const val GRAPHIC_CONTRAST = 3.0

/** The WCAG relative luminance of the colour, its alpha ignored. */
fun Color.relativeLuminance(): Double {
    fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <=
            LINEAR_LIMIT
        ) {
            c / LINEAR_DIVISOR
        } else {
            ((c + GAMMA_OFFSET) / GAMMA_DIVISOR).pow(GAMMA)
        }
    }
    return RED * channel(red) + GREEN * channel(green) + BLUE * channel(blue)
}

/** The WCAG contrast ratio of two colours, from 1 to 21. */
fun contrastRatio(
    a: Color,
    b: Color,
): Double {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    return (maxOf(la, lb) + FLARE) / (minOf(la, lb) + FLARE)
}

/** White or [OnLight] on [background], whichever has the higher contrast (TH-02). */
fun onColorFor(background: Color): Color =
    if (contrastRatio(Color.White, background) >= contrastRatio(OnLight, background)) {
        Color.White
    } else {
        OnLight
    }

/** [a] moved towards [b] by [fraction] (0 keeps [a], 1 gives [b]), opaque. */
fun mix(
    a: Color,
    b: Color,
    fraction: Float,
): Color =
    Color(
        red = a.red + (b.red - a.red) * fraction,
        green = a.green + (b.green - a.green) * fraction,
        blue = a.blue + (b.blue - a.blue) * fraction,
    )

/** The colour without transparency, as it shows over [under]. */
fun Color.opaqueOver(under: Color): Color =
    if (alpha >=
        1f
    ) {
        this
    } else {
        mix(under, copy(alpha = 1f), alpha)
    }

/**
 * [color], moved towards the text colour of [backgrounds] just enough to reach [minimum]
 * contrast on every one of them: a role or category colour stays recognisable and reads
 * in both themes (TH-05, AX-01).
 */
fun readableOn(
    color: Color,
    backgrounds: List<Color>,
    minimum: Double = TEXT_CONTRAST,
): Color {
    val target = onColorFor(backgrounds.first())
    var fraction = 0f
    var result = color.copy(alpha = 1f)
    while (backgrounds.any { contrastRatio(result, it) < minimum } && fraction < 1f) {
        fraction = (fraction + STEP).coerceAtMost(1f)
        result = mix(color.copy(alpha = 1f), target, fraction)
    }
    return result
}

private const val RED = 0.2126
private const val GREEN = 0.7152
private const val BLUE = 0.0722
private const val LINEAR_LIMIT = 0.04045
private const val LINEAR_DIVISOR = 12.92
private const val GAMMA_OFFSET = 0.055
private const val GAMMA_DIVISOR = 1.055
private const val GAMMA = 2.4
private const val FLARE = 0.05
private const val STEP = 0.05f
