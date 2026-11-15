// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ExcerptTest {
    private fun excerpt(
        markdown: String,
        length: Int = EXCERPT_LENGTH,
    ) = IdejoMarkdown.parse(markdown).excerpt("(spoiler)", length)

    @Test
    fun `CO-16 an excerpt is the words, formats left out`() {
        assertEquals(
            "Which twine do you use? Jute.",
            excerpt("**Which** _twine_ do you use?\n\n- Jute."),
        )
        assertEquals("The end: (spoiler) :tada:", excerpt("The end: ||they plant it all|| :tada:"))
        assertEquals("a photo", excerpt("![a photo](/uploads/1.webp)"))
    }

    @Test
    fun `CO-16 a long excerpt is cut on a word`() {
        assertEquals("Tomatoes need…", excerpt("Tomatoes need staking early", length = 16))
    }
}
