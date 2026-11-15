// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.model.ApiError
import org.idejo.app.core.model.Commit
import org.idejo.app.core.model.CommitResult
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.MessageState
import org.idejo.app.core.model.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.idejo.app.core.model.ComposerState as ServerComposer

/** Reply, forward and edit (CO-16 to CO-18). */
class ComposerModesTest {
    private val ada = Person(id = "u_1", displayName = "Ada Lovelace")
    private val leo = Person(id = "u_2", displayName = "Leo Moreau")
    private val quote = Quoted("m_5", "Leo Moreau", "Jute, soft on the stems.")
    private val markdown = inlineQuote(quote, "Replying to Leo Moreau:")

    private fun message(
        id: String,
        body: String,
        author: Person = ada,
        state: MessageState = MessageState.Open,
    ) = Message(id = id, topicId = "t_1", author = author, state = state, bodyMd = body)

    private fun run(
        vararg events: ComposerEvent,
        state: ComposerState = ComposerState(),
    ): Pair<ComposerState, List<ComposerEffect>> =
        events.fold(state to emptyList()) { (current, effects), event ->
            ComposerMachine.reduce(current, event).let { it.state to effects + it.effects }
        }

    private val open =
        ComposerState(text = "Hi. ", sent = "Hi.", openMessageId = "m_9", typingSent = true)

    @Test
    fun `CO-16 with nothing being written, the composer replies`() {
        val (state, effects) =
            run(
                ComposerEvent.Reply(quote, markdown, above = false),
                ComposerEvent.Typed("Mine too"),
                ComposerEvent.Enter(0),
            )
        assertEquals(quote, state.replying)
        assertEquals(
            ComposerEffect.Send(Commit("Mine too", replyTo = listOf("m_5"))),
            effects.last(),
        )
    }

    @Test
    fun `CO-16 a reply above the open message is quoted inline at the end`() {
        val (state, effects) = run(ComposerEvent.Reply(quote, markdown, above = true), state = open)
        assertEquals("Hi.\n\n$markdown\n\n", state.text)
        assertNull(state.replying)
        assertTrue(effects.isEmpty())
        assertEquals(
            "> [Replying to Leo Moreau:](/m/m_5)\n> [Jute, soft on the stems.](/m/m_5)",
            markdown,
        )
    }

    @Test
    fun `CO-16 a reply below starts a new message, the open one kept`() {
        val (state, effects) =
            run(
                ComposerEvent.Reply(quote, markdown, above = false),
                ComposerEvent.Typed("Mine too"),
                ComposerEvent.Enter(0),
                state = open,
            )
        assertEquals(Draft("m_9", "Hi."), state.draftOver)
        assertEquals(
            ComposerEffect.Send(Commit("Mine too", new = true, replyTo = listOf("m_5"))),
            effects.single(),
        )
        val (opened, _) =
            run(
                ComposerEvent.Answered(
                    Commit("Mine too", new = true, replyTo = listOf("m_5")),
                    CommitResult(
                        message("m_10", "Mine too."),
                        ServerComposer(openMessageId = "m_10"),
                    ),
                ),
                state = state,
            )
        assertNull(opened.draftOver)
        assertTrue(opened.replying!!.sent)
        // Cancel reply only before the first Enter.
        assertEquals(opened, run(ComposerEvent.CancelReply, state = opened).first)
    }

    @Test
    fun `CO-16 what is not sent goes first, then the reply`() {
        val typed = open.copy(text = "Hi. there")
        val (sending, effects) =
            run(
                ComposerEvent.Reply(quote, markdown, above = false),
                state = typed,
            )
        assertEquals(listOf(ComposerEffect.Send(Commit("Hi. there"))), effects)
        val (state, _) =
            run(
                ComposerEvent.Answered(
                    Commit("Hi. there"),
                    CommitResult(
                        message("m_9", "Hi. there."),
                        ServerComposer(openMessageId = "m_9"),
                    ),
                ),
                state = sending,
            )
        assertEquals(Draft("m_9", "Hi. there."), state.draftOver)
        assertEquals(quote, state.replying)
        assertEquals("", state.text)
    }

    @Test
    fun `CO-16 too_soon offers Append to my message, back to the open one`() {
        val replying =
            run(
                ComposerEvent.Reply(quote, markdown, false),
                ComposerEvent.Typed("x"),
                state = open,
            ).first
        val tooSoon =
            ApiError(
                ApiError.TOO_SOON,
                "You cannot post a new message yet. Append your old message.",
            )
        val (refused, _) =
            run(
                ComposerEvent.Enter(0),
                ComposerEvent.Failed(Commit("x", new = true), ApiProblem.Server(409, tooSoon)),
                state = replying,
            )
        assertEquals(ComposerError.Refused(tooSoon), refused.error)
        val (back, _) = run(ComposerEvent.AppendToOpen, state = refused)
        assertEquals("Hi. x", back.text)
        assertEquals("m_9", back.openMessageId)
        assertNull(back.replying)
        assertNull(back.error)
    }

    @Test
    fun `CO-17 a forward starts a new message carrying it, Cancel forward goes back`() {
        val forwarding = Forwarding(message("m_3", "Sungold", leo, MessageState.Closed), "Seeds")
        val (state, _) =
            run(
                ComposerEvent.Forward(forwarding),
                ComposerEvent.Typed("Look"),
                ComposerEvent.Enter(0),
            )
        assertEquals(Commit("Look", forwardOf = "m_3"), state.inFlight)
        val (kept, _) = run(ComposerEvent.Forward(forwarding), state = open)
        assertEquals(Draft("m_9", "Hi."), kept.draftOver)
        val (cancelled, _) = run(ComposerEvent.CancelForward, state = kept)
        assertNull(cancelled.forwarding)
        assertEquals("m_9", cancelled.openMessageId)
        assertEquals("Hi. ", cancelled.text)
    }

    @Test
    fun `CO-18 Edit opens the message in the composer, Enter saves, Ctrl+Enter ends`() {
        val closed = message("m_2", "Which twine?", state = MessageState.Closed)
        val (editing, _) =
            run(
                ComposerEvent.Edit(closed),
                ComposerEvent.Typed("Which twine do you use?"),
                state = open,
            )
        assertEquals("m_2", editing.placeId)
        assertEquals(ComposerStatus.Editing, editing.status)
        assertNull(editing.line)
        val (saving, effects) = run(ComposerEvent.Enter(0), state = editing)
        assertEquals(
            listOf(ComposerEffect.SaveEdit("m_2", "Which twine do you use?", false)),
            effects,
        )
        val (saved, _) =
            run(
                ComposerEvent.Edited(closed.copy(bodyMd = "Which twine do you use?"), false),
                state = saving,
            )
        assertEquals("Which twine do you use?", saved.sent)
        val (_, finish) = run(ComposerEvent.Complete, state = saved)
        assertEquals(
            listOf(ComposerEffect.SaveEdit("m_2", "Which twine do you use?", true)),
            finish,
        )
        val (ended, _) = run(ComposerEvent.Edited(closed, true), state = saved)
        assertEquals(open, ended.copy(serverAtMillis = 0))
    }

    @Test
    fun `CO-18 emptying an edit asks before deleting`() {
        val closed = message("m_2", "Which twine?", state = MessageState.Closed)
        val (asked, none) =
            run(
                ComposerEvent.Edit(closed),
                ComposerEvent.Typed(""),
                ComposerEvent.Enter(0),
                state = open,
            )
        assertTrue(asked.confirmDelete)
        assertTrue(none.isEmpty())
        assertFalse(run(ComposerEvent.CancelDelete, state = asked).first.confirmDelete)
        val (deleting, effects) = run(ComposerEvent.ConfirmDelete, state = asked)
        assertEquals(listOf(ComposerEffect.Delete("m_2")), effects)
        assertEquals("m_9", run(ComposerEvent.Deleted, state = deleting).first.openMessageId)
    }

    @Test
    fun `CO-16 an excerpt of a whole-message spoiler gives nothing away`() {
        val covered =
            message(
                "m_4",
                "they plant it all",
            ).copy(
                spoiler =
                    org.idejo.app.core.model
                        .Spoiler("the ending"),
            )
        assertEquals(
            "(spoiler: the ending)",
            quoteOf(covered, "(spoiler)") { "(spoiler: $it)" }.excerpt,
        )
        assertEquals(
            "[a\\_b](x)",
            "[" +
                inlineQuote(
                    Quoted("m", "a_b", ""),
                    "a_b",
                ).substringAfter("[").substringBefore("]") +
                "](x)",
        )
    }
}
