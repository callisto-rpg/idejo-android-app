// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.idejo.app.core.model.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The `!` commands and images, in the box (CO-13, CO-14, CO-20). */
class ComposerCommandsTest {
    private fun caret(text: String) = TextFieldValue(text, TextRange(text.length))

    @Test
    fun `CO-13 after an exclamation mark, the commands narrowing as typed`() {
        assertEquals(SuggestAt('!', "po", 0), caret("!po").suggestAt())
        val commands = listOf(Command("poll"), Command("plugin-x"), Command("schedule"))
        assertEquals(
            listOf("!poll", "!plugin-x"),
            commandSuggestions(commands, "p").map { it.label },
        )
    }

    @Test
    fun `CO-13 typed with an argument, a command runs at its closing colon`() {
        val typed = caret("So: !spoiler:Ending of book 3:").typedCommand()
        assertEquals(TypedCommand("spoiler", "Ending of book 3", 4), typed)
        assertEquals("So: ", caret("So: !spoiler:Ending of book 3:").without(4).text)
        assertNull(caret("!spoiler:open").typedCommand())
    }

    @Test
    fun `CO-13 schedule takes only the text not sent yet`() {
        val state = ComposerState(text = "Hi. later", sent = "Hi.")
        assertEquals("later", scheduledText(state))
        assertEquals("Hi. ", afterScheduled(state))
        assertNull(scheduledText(ComposerState(text = "Ho. later", sent = "Hi.")))
        assertNull(scheduledText(ComposerState(text = "Hi. ", sent = "Hi.")))
        assertEquals(1.5, hoursOf("1,5")!!, 0.0)
        assertNull(hoursOf("169"))
        assertNull(hoursOf("0"))
    }

    @Test
    fun `CO-13 collapse goes at the start of the line`() {
        assertEquals("a\n!collapse b", caret("a\nb").atLineStart("!collapse ").text)
    }

    @Test
    fun `CO-14 an image at the end of the message, as an image pill, with its description`() {
        val box = caret("Look").appendLine("![image](/uploads/up_1.png)")
        assertEquals("Look\n![image](/uploads/up_1.png)", box.text)
        assertEquals(
            "![Sungold \\[ripe\\]](/uploads/up_1.png)",
            withAlt("![image](/uploads/up_1.png)", "Sungold [ripe]"),
        )
        val text = "Look ![ripe](/uploads/up_1.png) now"
        val shown = Swapped(text, markdownLook(text, graphical = false).swaps).shown
        assertEquals("Look image: ripe now", shown)
        val graphical = Swapped(text, markdownLook(text, graphical = true).swaps).shown
        assertEquals("Look image: ripe now", graphical)
    }

    @Test
    fun `CO-15 a GIF of the provider shows as the GIF pill, other pictures as they are`() {
        val pills = Pills(gifHosts = listOf("https://static.klipy.com"))
        val text = "![Cat](https://static.klipy.com/1.gif) ![x](https://elsewhere.org/1.gif)"
        val shown = Swapped(text, markdownLook(text, graphical = false, pills).swaps).shown
        assertEquals("GIF: Cat ![x](https://elsewhere.org/1.gif)", shown)
        val gif =
            org.idejo.app.core.api
                .Gif("1", "cat", "A [cat]", "p", "https://f/1.gif")
        assertEquals("![A  cat](https://f/1.gif)", gifMarkdown(gif, "GIF"))
    }
}
