// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * A forum's five colours (TH-01), from `GET /server` → `theme` (IX-01): primary, secondary,
 * tertiary and its light and dark backgrounds. [colorScheme] maps them to Material 3 (TH-02).
 */
@Immutable
data class ForumTheme(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val lightBackground: Color,
    val darkBackground: Color,
) {
    /** One line to cache, so a forum opens in its colours at once (TH-06); see [decode]. */
    fun encode(): String = colors().joinToString(" ") { it.toHex() }

    private fun colors() = listOf(primary, secondary, tertiary, lightBackground, darkBackground)

    companion object {
        /** idejo's own colours (TH-08): before sign-in, and for forums without IX-01. */
        val Default =
            ForumTheme(
                primary = Color(0xFFDAA304),
                secondary = Color(0xFFF2C94C),
                tertiary = Color(0xFFB8860B),
                lightBackground = Color(0xFFFDFCF6),
                darkBackground = Color(0xFF000000),
            )

        /**
         * The server's `#rrggbb` or `#rrggbbaa` strings (TH-01). A missing or malformed one
         * keeps idejo's own colour (TH-08), so a bad value never breaks the forum.
         */
        fun fromHex(
            primary: String?,
            secondary: String?,
            tertiary: String?,
            lightBackground: String?,
            darkBackground: String?,
        ): ForumTheme =
            ForumTheme(
                primary = parseHexColor(primary) ?: Default.primary,
                secondary = parseHexColor(secondary) ?: Default.secondary,
                tertiary = parseHexColor(tertiary) ?: Default.tertiary,
                lightBackground = parseHexColor(lightBackground) ?: Default.lightBackground,
                darkBackground = parseHexColor(darkBackground) ?: Default.darkBackground,
            )

        /** A theme cached by [encode], or null when there is none or it is unreadable. */
        fun decode(encoded: String?): ForumTheme? {
            val parts = encoded?.split(" ")?.takeIf { it.size == COLOURS } ?: return null
            val colors = parts.map { parseHexColor(it) ?: return null }
            return ForumTheme(colors[0], colors[1], colors[2], colors[3], colors[4])
        }

        private const val COLOURS = 5
    }
}

/** `#rrggbb` or `#rrggbbaa` (the leading `#` optional), or null when it is neither. */
fun parseHexColor(hex: String?): Color? {
    val digits = hex?.trim()?.removePrefix("#") ?: return null
    val valid =
        (digits.length == RGB_DIGITS || digits.length == RGBA_DIGITS) &&
            digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
    if (!valid) return null
    val rgb = digits.substring(0, RGB_DIGITS).toLong(HEX)
    val alpha =
        if (digits.length ==
            RGBA_DIGITS
        ) {
            digits.substring(RGB_DIGITS).toLong(HEX)
        } else {
            OPAQUE
        }
    return Color((alpha shl ALPHA_SHIFT or rgb).toInt())
}

/** `#rrggbbaa`, as the server writes colours. */
internal fun Color.toHex(): String {
    val argb = toArgb().toLong() and ARGB_MASK
    val rgb = argb and RGB_MASK
    val alpha = argb ushr ALPHA_SHIFT
    return "#%06x%02x".format(rgb, alpha)
}

private const val RGB_DIGITS = 6
private const val RGBA_DIGITS = 8
private const val HEX = 16
private const val OPAQUE = 0xFFL
private const val ALPHA_SHIFT = 24
private const val ARGB_MASK = 0xFFFFFFFFL
private const val RGB_MASK = 0xFFFFFFL
