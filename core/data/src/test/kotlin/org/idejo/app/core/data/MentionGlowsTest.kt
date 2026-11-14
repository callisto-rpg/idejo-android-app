// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import org.idejo.app.core.model.Me
import org.idejo.app.core.model.Mention
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.MessageState
import org.idejo.app.core.model.Person
import org.idejo.app.core.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which unread messages glow, and in which colour (TV-07, IX-12). */
class MentionGlowsTest {
    private val ada =
        Me(
            id = "u_ada",
            displayName = "Ada",
            roles = listOf(Role("Member", slug = "member"), Role("Gardener", slug = "gardener")),
        )
    private val mina = Person(id = "u_mina", displayName = "Mina")
    private val atAda = Mention("user", "@Ada", "@ada", id = "u_ada")
    private val atLeo = Mention("user", "@Leo", "@leo", id = "u_leo")
    private val gardeners =
        Mention("role", "+Gardener", "+gardener", id = "gardener", slug = "gardener")
    private val beekeepers =
        Mention("role", "+Beekeeper", "+beekeeper", id = "beekeeper", slug = "beekeeper")

    private fun message(
        id: String,
        vararg mentions: Mention,
        author: Person = mina,
        state: MessageState = MessageState.Closed,
    ) = Message(id, "t_1", author, state, mentions = mentions.toList())

    @Test
    fun `TV-07 from the first unread on, an @mention glows red, a +role of the member's blue`() {
        val messages =
            listOf(
                message("m_1", atAda),
                message("m_2", atLeo),
                message("m_3", gardeners),
                message("m_4", gardeners, atAda),
                message("m_5", beekeepers),
            )
        assertEquals(
            mapOf("m_3" to Glow.Role, "m_4" to Glow.Mention),
            mentionGlows(messages, from = "m_2", me = ada),
        )
    }

    @Test
    fun `TV-07 not the member's own, deleted ones, nor without an unread message`() {
        val messages =
            listOf(
                message("m_1", atAda, author = Person(id = "u_ada", displayName = "Ada")),
                message("m_2", atAda, state = MessageState.Deleted),
                message("m_3", atAda),
            )
        assertEquals(mapOf("m_3" to Glow.Mention), mentionGlows(messages, "m_1", ada))
        assertTrue(mentionGlows(messages, from = null, me = ada).isEmpty())
        assertTrue(mentionGlows(messages, from = "m_1", me = null).isEmpty())
    }

    @Test
    fun `TV-07 the tags that pulse are the member's and their roles'`() {
        assertTrue(atAda.namesMember(ada))
        assertTrue(gardeners.namesMember(ada))
        assertFalse(atLeo.namesMember(ada))
        assertFalse(beekeepers.namesMember(ada))
    }
}
