// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The graphical composer's look, controls and suggestions (CO-10 to CO-12). */
class GraphicalComposerTest {
    private fun shown(text: String) =
        Swapped(text, markdownLook(text, graphical = true).swaps).shown

    private fun caret(
        text: String,
        at: Int = text.length,
    ) = TextFieldValue(text, TextRange(at))

    @Test
    fun `CO-11 finished signs disappear, unfinished ones stay`() {
        assertEquals("a bold word", shown("a **bold** word"))
        assertEquals("a **bold", shown("a **bold"))
        assertEquals(
            "struck, marked, code, hidden",
            shown("~~struck~~, ==marked==, `code`, ||hidden||"),
        )
        assertEquals("italics and more", shown("*italics* and _more_"))
        assertEquals("see idejo", shown("see [idejo](https://idejo.org)"))
        assertEquals("`**not bold**`".length - 2, shown("`**not bold**`").length)
    }

    @Test
    fun `CO-11 titles, bullets, numbers and quotes as they will look`() {
        assertEquals("Plans\n• seeds\n1. sow\nquoted", shown("# Plans\n- seeds\n1. sow\n> quoted"))
        val look = markdownLook("# Plans\n> quoted", graphical = true)
        assertEquals(listOf(Mark.Title, Mark.Quote), look.styles.map { it.mark })
    }

    @Test
    fun `CO-11 without the graphical composer the signs stay, the text styled`() {
        val look = markdownLook("a **bold** ||secret||", graphical = false)
        assertTrue(look.swaps.isEmpty())
        assertEquals(listOf(Mark.Bold, Mark.Spoiler), look.styles.map { it.mark })
        assertEquals(Styled(Mark.Bold, 4, 8), look.styles.first())
    }

    @Test
    fun `CO-11 the caret maps both ways around hidden signs`() {
        val text = "a **b** c"
        val swapped = Swapped(text, markdownLook(text, graphical = true).swaps)
        assertEquals("a b c", swapped.shown)
        assertEquals(2, swapped.shown(2))
        assertEquals(2, swapped.shown(4))
        assertEquals(5, swapped.shown(text.length))
        assertEquals(text.length, swapped.text(5))
        assertEquals(4, swapped.text(2))
        for (i in 0..swapped.shown.length) assertTrue(swapped.text(i) in 0..text.length)
    }

    @Test
    fun `CO-10 the pencil formats the selection`() {
        val words = TextFieldValue("grow tomatoes", TextRange(5, 13))
        assertEquals("grow **tomatoes**", words.wrap(Format.Bold).text)
        assertEquals(TextRange(7, 15), words.wrap(Format.Bold).selection)
        assertEquals("grow ~~tomatoes~~", words.wrap(Format.Strike).text)
        val link = words.link()
        assertEquals("grow [tomatoes](https://)", link.text)
        assertEquals("https://", link.text.substring(link.selection.min, link.selection.max))
        val lines = TextFieldValue("one\ntwo", TextRange(0, 7))
        assertEquals("1. one\n2. two", lines.lines { "$it. " }.text)
        assertEquals("> one\n> two", lines.lines { "> " }.text)
    }

    @Test
    fun `CO-11 Shift+Enter continues a list, ends it on an empty item`() {
        assertEquals("- seeds\n- ", caret("- seeds").newLine().text)
        assertEquals("1. sow\n2. ", caret("1. sow").newLine().text)
        assertEquals("- seeds\n", caret("- seeds\n- ").newLine().text)
        assertEquals("plain\n", caret("plain").newLine().text)
    }

    @Test
    fun `CO-11 Backspace at the start of an item removes its bullet`() {
        val before = caret("- seeds\n- ")
        val removed = bulletRemoved(before, caret("- seeds\n-", 9))
        assertEquals("- seeds\n", removed!!.text)
        assertNull(bulletRemoved(caret("- seeds"), caret("- seed")))
    }

    @Test
    fun `CO-12 a sign starts the suggestions, narrowing as typed`() {
        assertEquals(SuggestAt('@', "ro", 6), caret("Hello @ro").suggestAt())
        assertEquals(SuggestAt('$', "", 0), caret("$").suggestAt())
        assertEquals(SuggestAt('#', "garden-pl", 4), caret("see #garden-pl").suggestAt())
        assertNull(caret("mail@ro").suggestAt())
        assertNull(caret("Hello @ro bert").suggestAt())
        val chosen = caret("Hello @ro").accept(SuggestAt('@', "ro", 6), "robert")
        assertEquals("Hello @robert ", chosen.text)
        assertEquals(TextRange(14), chosen.selection)
    }
}
