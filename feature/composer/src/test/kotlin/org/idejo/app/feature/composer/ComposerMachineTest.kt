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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import org.idejo.app.core.model.ComposerState as ServerComposer

class ComposerMachineTest {
    private val ada = Person(id = "u_1", displayName = "Ada Lovelace")

    private fun message(
        body: String,
        state: MessageState = MessageState.Open,
    ) = Message(id = "m_9", topicId = "t_1", author = ada, state = state, bodyMd = body)

    private fun open(id: String? = "m_9") = ServerComposer(openMessageId = id)

    /** Runs [events] from [state]: the last state, and every effect asked for. */
    private fun run(
        vararg events: ComposerEvent,
        state: ComposerState = ComposerState(),
    ): Pair<ComposerState, List<ComposerEffect>> =
        events.fold(state to emptyList()) { (current, effects), event ->
            ComposerMachine.reduce(current, event).let { it.state to effects + it.effects }
        }

    private fun sentState(body: String = "Hi.") =
        ComposerState(text = "$body ", sent = body, openMessageId = "m_9", typingSent = true)

    @Test
    fun `CO-02 the first keystroke of a new message sends typing once`() {
        val (_, effects) =
            run(
                ComposerEvent.Typed("H"),
                ComposerEvent.Typed("Hi"),
                ComposerEvent.Typed(""),
                ComposerEvent.Typed("Ho"),
            )
        assertEquals(listOf(ComposerEffect.Typing), effects)
    }

    @Test
    fun `CO-02 no typing while a message is open`() {
        val (_, effects) = run(ComposerEvent.Typed("Hi. there"), state = sentState())
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `CO-02 Enter commits the whole open message with break false`() {
        val (state, effects) =
            run(ComposerEvent.Typed("Hi. there  "), ComposerEvent.Enter(0), state = sentState())
        val commit = Commit("Hi. there", breakMessage = false)
        assertEquals(listOf(ComposerEffect.Send(commit)), effects)
        assertEquals(commit, state.inFlight)
    }

    @Test
    fun `CO-02 the answer's message replaces the local text and the state updates`() {
        val attempt = Commit("Hi")
        val sending = ComposerState(text = "Hi", inFlight = attempt, typingSent = true)
        val (state, _) =
            run(
                ComposerEvent.Answered(attempt, CommitResult(message("Hi."), open())),
                state = sending,
            )
        assertEquals("Hi.", state.sent)
        assertEquals("Hi. ", state.text)
        assertEquals("m_9", state.openMessageId)
        assertNull(state.inFlight)
        assertEquals(open(), state.server)
    }

    @Test
    fun `CO-09 automatic punctuation shows as the server answered it`() {
        val attempt = Commit("Hi. there")
        val sending = sentState().copy(text = "Hi. there", inFlight = attempt)
        val (state, _) =
            run(
                ComposerEvent.Answered(attempt, CommitResult(message("Hi. there."), open())),
                state = sending,
            )
        assertEquals("Hi. there. ", state.text)
        assertEquals(ComposerStatus.Sent, state.status)
    }

    @Test
    fun `CO-02 text typed while a commit is on its way stays after the answer`() {
        val attempt = Commit("Hi")
        val sending = ComposerState(text = "Hi and more", inFlight = attempt)
        val (state, _) =
            run(
                ComposerEvent.Answered(attempt, CommitResult(message("Hi."), open())),
                state = sending,
            )
        assertEquals("Hi. and more", state.text)
        assertEquals(ComposerStatus.SentAppendsUnsent, state.status)
    }

    @Test
    fun `CO-02 an Enter during a commit is sent after its answer`() {
        val (state, effects) =
            run(
                ComposerEvent.Typed("Hi"),
                ComposerEvent.Enter(0),
                ComposerEvent.Typed("Hi there"),
                ComposerEvent.Enter(5_000),
                ComposerEvent.Answered(Commit("Hi"), CommitResult(message("Hi."), open())),
            )
        assertEquals(
            listOf(
                ComposerEffect.Typing,
                ComposerEffect.Send(Commit("Hi")),
                ComposerEffect.Send(Commit("Hi. there")),
            ),
            effects,
        )
        assertEquals(Commit("Hi. there"), state.inFlight)
    }

    @Test
    fun `CO-02 Enter with nothing new sends nothing`() {
        val (_, effects) = run(ComposerEvent.Enter(0), state = sentState())
        assertTrue(effects.isEmpty())
        val (_, none) = run(ComposerEvent.Enter(0))
        assertTrue(none.isEmpty())
    }

    @Test
    fun `CO-03 enter three times quickly completes the message`() {
        val (_, effects) =
            run(
                ComposerEvent.Enter(0),
                ComposerEvent.Enter(400),
                ComposerEvent.Enter(900),
                state = sentState(),
            )
        assertEquals(listOf(ComposerEffect.Send(Commit("Hi.", breakMessage = true))), effects)
    }

    @Test
    fun `CO-03 typing between Enters, or a pause, starts the run again`() {
        val (_, typedBetween) =
            run(
                ComposerEvent.Enter(0),
                ComposerEvent.Enter(400),
                ComposerEvent.Typed("Hi. x"),
                ComposerEvent.Enter(800),
                state = sentState(),
            )
        assertTrue(typedBetween.none { (it as? ComposerEffect.Send)?.commit?.breakMessage == true })
        val (_, paused) =
            run(
                ComposerEvent.Enter(0),
                ComposerEvent.Enter(400),
                ComposerEvent.Enter(400 + ENTER_RUN_MILLIS + 1),
                state = sentState(),
            )
        assertTrue(paused.isEmpty())
    }

    @Test
    fun `CO-03 Ctrl+Enter completes with break true`() {
        val (_, effects) = run(ComposerEvent.Complete, state = sentState())
        assertEquals(listOf(ComposerEffect.Send(Commit("Hi.", breakMessage = true))), effects)
    }

    @Test
    fun `CO-03 the soft keyboard's newline is an Enter, never inserted`() {
        assertEquals("Hi", softReturn("Hi", "Hi\n"))
        assertEquals("Hi there", softReturn("Hi there", "Hi\n there"))
        assertNull(softReturn("Hi", "Hi!"))
        assertNull(softReturn("Hi", "Hi\n\n"))
        assertNull(softReturn("", "x"))
    }

    @Test
    fun `CO-05 a completed message says sent and committed, then the composer is new`() {
        val attempt = Commit("Hi.", breakMessage = true)
        val (state, _) =
            run(
                ComposerEvent.Answered(
                    attempt,
                    CommitResult(message("Hi.", MessageState.Closed), open(null)),
                ),
                state = sentState().copy(inFlight = attempt),
            )
        assertEquals(ComposerStatus.SentAndCommitted, state.status)
        assertEquals("", state.text)
        assertEquals("", state.sent)
        assertNull(state.openMessageId)
        val (after, effects) =
            run(ComposerEvent.CommittedShown, ComposerEvent.Typed("Next"), state = state)
        assertEquals(ComposerStatus.Unsent, after.status)
        assertEquals(listOf(ComposerEffect.Typing), effects)
    }

    @Test
    fun `CO-05 the status follows what is sent`() {
        assertEquals(ComposerStatus.Empty, ComposerState().status)
        assertEquals(ComposerStatus.Unsent, ComposerState(text = "Hi").status)
        assertEquals(ComposerStatus.Sent, sentState().status)
        assertEquals(
            ComposerStatus.SentAppendsUnsent,
            sentState().copy(text = "Hi. there").status,
        )
    }

    @Test
    fun `CO-04 unsent text is what the server does not hold`() {
        assertEquals(0..1, unsentRange("", "Hi"))
        assertNull(unsentRange("Hi.", "Hi. "))
        assertEquals(3..8, unsentRange("Hi.", "Hi. there"))
        // Editing inside sent text makes the changed part unsent.
        assertEquals(0..1, unsentRange("Hi. there.", "Yo. there."))
        // A deletion leaves an empty range where the text went.
        assertTrue(unsentRange("Hi. there.", "Hi. .")!!.isEmpty())
    }

    @Test
    fun `CO-04 text just sent glows`() {
        val attempt = Commit("Hi. there")
        val (state, _) =
            run(
                ComposerEvent.Answered(attempt, CommitResult(message("Hi. there."), open())),
                state = sentState().copy(text = "Hi. there", inFlight = attempt),
            )
        assertEquals(3..9, state.justSent)
        val (typed, _) = run(ComposerEvent.Typed("Hi. there. x"), state = state)
        assertNull(typed.justSent)
    }

    @Test
    fun `CO-08 a refusal shows the server's message and keeps the text`() {
        val attempt = Commit("Again")
        val refusal =
            ApiError(
                ApiError.TOO_SOON,
                "You cannot post a new message yet. Append your old message.",
            )
        val (state, _) =
            run(
                ComposerEvent.Failed(attempt, ApiProblem.Server(409, refusal)),
                state = ComposerState(text = "Again", inFlight = attempt),
            )
        assertEquals(ComposerError.Refused(refusal), state.error)
        assertEquals("Again", state.text)
        assertNull(state.inFlight)
        assertNull(run(ComposerEvent.ErrorSeen, state = state).first.error)
    }

    @Test
    fun `CO-08 offline keeps everything typed`() {
        val attempt = Commit("Hi. there")
        val (state, _) =
            run(
                ComposerEvent.Failed(attempt, ApiProblem.Network(IOException("offline"))),
                state = sentState().copy(text = "Hi. there", inFlight = attempt),
            )
        assertEquals(ComposerError.Unreachable, state.error)
        assertEquals("Hi. there", state.text)
        assertEquals(ComposerStatus.SentAppendsUnsent, state.status)
        // The next Enter sends it again.
        val (_, effects) = run(ComposerEvent.Enter(0), state = state)
        assertEquals(listOf(ComposerEffect.Send(Commit("Hi. there"))), effects)
    }

    @Test
    fun `GM-08 a composer opened later shows the author's open message`() {
        val (state, _) =
            run(ComposerEvent.Server(open(), message("Hi. there.")))
        assertEquals("Hi. there. ", state.text)
        assertEquals("Hi. there.", state.sent)
        assertEquals("m_9", state.openMessageId)
        assertEquals(ComposerStatus.Sent, state.status)
    }
}
