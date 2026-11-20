// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** DL-04: text shared to the app waits for its topic, then goes after the box's text. */
class SharedTextTest {
    @Test
    fun `DL-04 shared text waits, is taken once, and blank text is ignored`() {
        val shared = SharedImages()
        shared.offerText("   ")
        assertNull(shared.text.value)
        shared.offerText(" https://example.org/article \n")
        assertEquals("https://example.org/article", shared.takeText())
        assertNull(shared.takeText())
    }

    @Test
    fun `DL-04 dropped with the pictures when the card closes without a topic`() {
        val shared = SharedImages()
        shared.offerText("A quote")
        shared.drop()
        assertNull(shared.text.value)
    }

    @Test
    fun `DL-04 the text goes after what the box holds`() {
        assertEquals("A quote", after("", "A quote"))
        assertEquals("Look: A quote", after("Look: ", "A quote"))
    }
}
