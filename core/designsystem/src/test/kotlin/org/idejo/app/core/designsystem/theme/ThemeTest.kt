// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    private val garden = ForumTheme.fromHex("#3f7d3a", "#8a6a1f", "#2c6a8e", "#f7faf2", "#0b1410")
    private val tennis = ForumTheme.fromHex("#4fa3e0", "#c2410c", "#1f5f8b", "#f4f8fc", "#0b1118")

    // Odd colours a forum may choose: both backgrounds light, a near-white primary.
    private val odd = ForumTheme.fromHex("#fefefe", "#777777", "#ff00ff", "#eeeeee", "#dddddd")

    private val themes = listOf(ForumTheme.Default, garden, tennis, odd)

    private fun schemes(): List<ColorScheme> =
        themes.flatMap {
            listOf(it.colorScheme(false), it.colorScheme(true))
        }

    @Test
    fun `TH-01 colours are read as rrggbb or rrggbbaa`() {
        assertEquals(Color(0xFF3F7D3A), parseHexColor("#3f7d3a"))
        assertEquals(Color(0x80DAA304), parseHexColor("#daa30480"))
        assertEquals(Color(0xFFDAA304), parseHexColor("DAA304FF"))
        assertNull(parseHexColor("#daa3"))
        assertNull(parseHexColor("gold"))
        assertNull(parseHexColor(null))
    }

    @Test
    fun `TH-01 a missing or malformed colour keeps idejo's own`() {
        val theme = ForumTheme.fromHex("#3f7d3a", null, "nope", "#fff", "#000000")
        assertEquals(Color(0xFF3F7D3A), theme.primary)
        assertEquals(ForumTheme.Default.secondary, theme.secondary)
        assertEquals(ForumTheme.Default.tertiary, theme.tertiary)
        assertEquals(ForumTheme.Default.lightBackground, theme.lightBackground)
    }

    @Test
    fun `TH-02 on-colours are white or 111111, whichever has more contrast`() {
        val gold = ForumTheme.Default.colorScheme(false)
        assertEquals(OnLight, gold.onPrimary)
        assertEquals(Color.White, garden.colorScheme(false).onPrimary)
        assertEquals(OnLight, tennis.colorScheme(true).onPrimary)
        for (scheme in schemes()) {
            for (
            (color, on) in
            listOf(
                scheme.primary to scheme.onPrimary,
                scheme.secondary to scheme.onSecondary,
                scheme.tertiary to scheme.onTertiary,
                scheme.error to scheme.onError,
            )
            ) {
                assertTrue(on == Color.White || on == OnLight)
                val other = if (on == Color.White) OnLight else Color.White
                assertTrue(contrastRatio(on, color) >= contrastRatio(other, color))
            }
        }
    }

    @Test
    fun `TH-02 the page is the background, on-background the other background`() {
        val light = garden.colorScheme(false)
        val dark = garden.colorScheme(true)
        assertEquals(Color(0xFFF7FAF2), light.background)
        assertEquals(Color(0xFF0B1410), light.onBackground)
        assertEquals(Color(0xFF0B1410), dark.background)
        assertEquals(Color(0xFFF7FAF2), dark.onBackground)
    }

    @Test
    fun `TH-02 on-background is white or 111111 when the other background lacks contrast`() {
        val light = odd.colorScheme(false)
        assertEquals(OnLight, light.onBackground)
        assertTrue(contrastRatio(light.onBackground, light.background) >= TEXT_CONTRAST)
    }

    @Test
    fun `TH-02 surface is a little lighter than the page, surface-container a little darker`() {
        for (scheme in schemes()) {
            val page = scheme.background.relativeLuminance()
            assertTrue(scheme.surface.relativeLuminance() > page)
            assertTrue(scheme.surfaceContainer.relativeLuminance() <= page)
            assertTrue(
                scheme.surfaceContainerHighest.relativeLuminance() <=
                    scheme.surfaceContainer.relativeLuminance(),
            )
        }
        val light = ForumTheme.Default.colorScheme(false)
        assertTrue(contrastRatio(light.surface, light.background) < 1.1)
    }

    @Test
    fun `TH-02 every text colour reads at 4_5 to 1 on the page and its surfaces`() {
        for (scheme in schemes()) {
            for (under in listOf(
                scheme.background,
                scheme.surface,
                scheme.surfaceContainerHighest,
            )) {
                assertTrue(contrastRatio(scheme.onSurface, under) >= TEXT_CONTRAST)
                assertTrue(contrastRatio(scheme.onSurfaceVariant, under) >= TEXT_CONTRAST)
                assertTrue(contrastRatio(scheme.outline, under) >= GRAPHIC_CONTRAST)
            }
            assertTrue(
                contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer) >= TEXT_CONTRAST,
            )
            assertTrue(
                contrastRatio(scheme.onSecondaryContainer, scheme.secondaryContainer) >=
                    TEXT_CONTRAST,
            )
        }
    }

    @Test
    fun `TH-02 a translucent background is shown as it would look`() {
        val scheme =
            ForumTheme
                .fromHex(
                    null,
                    null,
                    null,
                    "#ffffff80",
                    "#00000080",
                ).colorScheme(false)
        assertEquals(1f, scheme.background.alpha)
    }

    @Test
    fun `TH-03 the theme follows the member's choice`() {
        assertTrue(ThemeMode.System.isDark(systemDark = true))
        assertFalse(ThemeMode.System.isDark(systemDark = false))
        assertFalse(ThemeMode.Light.isDark(systemDark = true))
        assertTrue(ThemeMode.Dark.isDark(systemDark = false))
    }

    @Test
    fun `TH-04 titles are Fraunces, text Rubik, in sp so the font scale applies`() {
        val type = IdejoTypography
        for (style in listOf(
            type.displaySmall,
            type.headlineSmall,
            type.titleLarge,
            type.titleMedium,
        )) {
            assertTrue(isFraunces(style.fontFamily))
            assertEquals(TextUnitType.Sp, style.fontSize.type)
        }
        for (style in listOf(
            type.bodyLarge,
            type.bodyMedium,
            type.bodySmall,
            type.labelLarge,
            type.labelSmall,
        )) {
            assertEquals(Rubik, style.fontFamily)
            assertEquals(TextUnitType.Sp, style.fontSize.type)
        }
        assertEquals(17f, type.titleMedium.fontSize.value)
        assertEquals(17f, type.bodyLarge.fontSize.value)
    }

    @Test
    fun `TH-05 role colours are made readable in both themes, category colours kept`() {
        val moderator = Color(0xFF6A3E8E)
        for (theme in themes) {
            for (dark in listOf(false, true)) {
                val scheme = theme.colorScheme(dark)
                val name = idejoColors(scheme, dark).roleName(moderator)
                assertTrue(contrastRatio(name, scheme.background) >= TEXT_CONTRAST)
                assertTrue(contrastRatio(name, scheme.surface) >= TEXT_CONTRAST)
            }
        }
        val light = garden.colorScheme(false)
        assertEquals(moderator, idejoColors(light, false).roleName(moderator))
        assertEquals(light.onBackground, idejoColors(light, false).roleName(null as Color?))
        assertEquals(Color(0xFF8A6A1F), idejoColors(light, false).category("#8a6a1f", light))
        assertEquals(light.secondary, idejoColors(light, false).category(null, light))
    }

    @Test
    fun `TH-06 a theme is cached as one line and read back`() {
        for (theme in themes + ForumTheme.fromHex("#daa30480", null, null, null, null)) {
            assertEquals(theme, ForumTheme.decode(theme.encode()))
        }
        assertEquals(
            "#daa304ff #f2c94cff #b8860bff #fdfcf6ff #000000ff",
            ForumTheme.Default.encode(),
        )
        assertNull(ForumTheme.decode(null))
        assertNull(ForumTheme.decode("#daa304ff"))
        assertNull(ForumTheme.decode("a b c d e"))
    }

    @Test
    fun `TH-07 switching forum cross-fades in 300 ms, none with reduced motion`() {
        assertEquals(300, themeFadeMillis(reducedMotion = false))
        assertEquals(0, themeFadeMillis(reducedMotion = true))
    }

    @Test
    fun `TH-08 idejo's default colours`() {
        val theme = ForumTheme.Default
        assertEquals(Color(0xFFDAA304), theme.primary)
        assertEquals(Color(0xFFF2C94C), theme.secondary)
        assertEquals(Color(0xFFB8860B), theme.tertiary)
        assertEquals(Color(0xFFFDFCF6), theme.lightBackground)
        assertEquals(Color(0xFF000000), theme.darkBackground)
    }

    @Test
    fun `TH-09 system bar icons read on the page`() {
        assertTrue(systemBarsDarkIcons(ForumTheme.Default.colorScheme(false).background))
        assertFalse(systemBarsDarkIcons(ForumTheme.Default.colorScheme(true).background))
        assertFalse(systemBarsDarkIcons(tennis.colorScheme(true).background))
    }

    @Test
    fun `TH-10 the dark-theme picture in the dark theme`() {
        assertEquals("dark.png", pictureUrl("light.png", "dark.png", dark = true))
        assertEquals("light.png", pictureUrl("light.png", "dark.png", dark = false))
        assertEquals("light.png", pictureUrl("light.png", null, dark = true))
        assertEquals("light.png", pictureUrl("light.png", "", dark = true))
    }

    @Test
    fun `AX-01 a colour is nudged just enough to read`() {
        val page = Color(0xFF0B1118)
        val nudged = readableOn(Color(0xFF6A3E8E), listOf(page))
        assertTrue(contrastRatio(nudged, page) >= TEXT_CONTRAST)
        assertTrue(contrastRatio(nudged, page) < 6.0)
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.01)
    }
}
