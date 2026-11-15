// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.model.ApiError
import org.idejo.app.core.model.Commit
import org.idejo.app.core.model.CommitResult
import org.idejo.app.core.model.Message
import java.time.Duration
import java.time.Instant
import org.idejo.app.core.model.ComposerState as ServerComposer

/** Three Enters in a row, each within this of the last, complete the message (CO-03). */
const val ENTER_RUN_MILLIS = 1_000L

/** How many Enters in a row complete the message (CO-03). */
private const val ENTER_RUN = 3

/** What the status bubble says (CO-05). */
enum class ComposerStatus {
    /** Nothing typed: no bubble. */
    Empty,
    Unsent,
    Sent,
    SentAppendsUnsent,

    /** For 3 s after a commit completed the message. */
    SentAndCommitted,
}

/** Why the last commit failed (CO-08). Nothing typed is lost either way. */
sealed interface ComposerError {
    /** The server's refusal: its own `message` is shown, never ours. */
    data class Refused(
        val error: ApiError,
        val retryAfter: Duration? = null,
    ) : ComposerError

    /** No answer (offline, timed out): the app's own words. */
    data object Unreachable : ComposerError
}

/**
 * The composer: the author's open message (idejo GM-08, CLAUDE.md → Growing-message
 * invariants). [text] is what the box shows; [sent] what the server holds of the open
 * message, as it answered it (automatic punctuation included, CO-09).
 */
data class ComposerState(
    val text: String = "",
    val sent: String = "",
    val openMessageId: String? = null,
    /** The server's composer state, for the countdown (CO-07); never worked out here. */
    val server: ServerComposer? = null,
    /** The commit on its way: one at a time, so the server sees them in order. */
    val inFlight: Commit? = null,
    /** An Enter (or a completion, `true`) made while [inFlight] was on its way. */
    val queuedBreak: Boolean? = null,
    /** `typing` went for this message (invariant 3): once per new message. */
    val typingSent: Boolean = false,
    val enterRun: Int = 0,
    val lastEnterAt: Long? = null,
    val error: ComposerError? = null,
    /** The text the last answer added, which glows then settles (CO-04). */
    val justSent: IntRange? = null,
    /** The message the last commit completed: "sent & committed" (CO-05). */
    val committedId: String? = null,
    /** When [server] came, on the device's clock: its `now` sets the offset (CO-07). */
    val serverAtMillis: Long = 0,
    /** When the open message opened, where its timer line starts (CO-07). */
    val openedAt: Instant? = null,
) {
    /** Slow mode's line, from the server's state (CO-07). */
    val line: TimerLine? get() = timerLine(server, openedAt, serverAtMillis)

    /** The part of [text] the server does not hold yet (CO-04), or null. */
    val unsent: IntRange? get() = unsentRange(sent, text)

    /** No message open, none on its way: a new message starts here. */
    val nothingSent: Boolean get() = openMessageId == null && sent.isEmpty() && inFlight == null

    /** Something typed or changed is not on the server yet. */
    val hasUnsent: Boolean get() = text.trimEnd() != sent.trimEnd()

    val status: ComposerStatus
        get() =
            when {
                committedId != null && text.isBlank() -> ComposerStatus.SentAndCommitted
                sent.isEmpty() && text.isBlank() -> ComposerStatus.Empty
                sent.isEmpty() -> ComposerStatus.Unsent
                hasUnsent -> ComposerStatus.SentAppendsUnsent
                else -> ComposerStatus.Sent
            }
}

/** What the member did, or what the server said. */
sealed interface ComposerEvent {
    /** The box's text changed (typing, pasting, Shift+Enter or **New line**). */
    data class Typed(
        val text: String,
    ) : ComposerEvent

    /** Enter: the soft keyboard's return or the hardware key, at [atMillis] (CO-02, CO-03). */
    data class Enter(
        val atMillis: Long,
    ) : ComposerEvent

    /** Ctrl+Enter or **Send & start new message**: `break: true` (CO-03). */
    data object Complete : ComposerEvent

    /** The commit [attempt] was answered. */
    data class Answered(
        val attempt: Commit,
        val result: CommitResult,
        /** When the answer came, on the device's clock. */
        val atMillis: Long = 0,
    ) : ComposerEvent

    /** The commit [attempt] failed. */
    data class Failed(
        val attempt: Commit,
        val problem: ApiProblem,
    ) : ComposerEvent

    /**
     * The server's composer state (`GET …/composer`, the `typing` answer), with the open
     * message when there is one, so a composer opened later shows it (invariant 5).
     */
    data class Server(
        val composer: ServerComposer,
        val open: Message? = null,
        /** When the state came, on the device's clock. */
        val atMillis: Long = 0,
    ) : ComposerEvent

    /** The "sent & committed" bubble's 3 s are over (CO-05). */
    data object CommittedShown : ComposerEvent

    data object ErrorSeen : ComposerEvent
}

/** What the composer asks the server for. */
sealed interface ComposerEffect {
    /** `POST …/composer/typing`, the first keystroke of a new message (CO-02). */
    data object Typing : ComposerEffect

    /** `POST …/composer/commit` (CO-02). */
    data class Send(
        val commit: Commit,
    ) : ComposerEffect
}

data class ComposerStep(
    val state: ComposerState,
    val effects: List<ComposerEffect> = emptyList(),
)

/**
 * The composer's transitions (CLAUDE.md → Architecture): pure, so each one has its test.
 * The server owns every rule: this only decides what to send and how to show the answer.
 */
object ComposerMachine {
    fun reduce(
        state: ComposerState,
        event: ComposerEvent,
    ): ComposerStep =
        when (event) {
            is ComposerEvent.Typed -> {
                typed(state, event.text)
            }

            is ComposerEvent.Enter -> {
                enter(state, event.atMillis)
            }

            ComposerEvent.Complete -> {
                send(state.copy(enterRun = 0, lastEnterAt = null), true)
            }

            is ComposerEvent.Answered -> {
                answered(state.copy(serverAtMillis = event.atMillis), event.attempt, event.result)
            }

            is ComposerEvent.Failed -> {
                failed(state, event.problem)
            }

            is ComposerEvent.Server -> {
                server(state.copy(serverAtMillis = event.atMillis), event.composer, event.open)
            }

            ComposerEvent.CommittedShown -> {
                ComposerStep(state.copy(committedId = null))
            }

            ComposerEvent.ErrorSeen -> {
                ComposerStep(state.copy(error = null))
            }
        }

    private fun typed(
        state: ComposerState,
        text: String,
    ): ComposerStep {
        if (text == state.text) return ComposerStep(state)
        val first =
            !state.typingSent &&
                state.openMessageId == null &&
                state.sent.isEmpty() &&
                text.isNotBlank()
        val next =
            state.copy(
                text = text,
                typingSent = state.typingSent || first,
                enterRun = 0,
                lastEnterAt = null,
                justSent = null,
            )
        return ComposerStep(next, if (first) listOf(ComposerEffect.Typing) else emptyList())
    }

    private fun enter(
        state: ComposerState,
        at: Long,
    ): ComposerStep {
        val last = state.lastEnterAt
        val run = if (last != null && at - last <= ENTER_RUN_MILLIS) state.enterRun + 1 else 1
        if (run >= ENTER_RUN) {
            return send(state.copy(enterRun = 0, lastEnterAt = null), true)
        }
        return send(state.copy(enterRun = run, lastEnterAt = at), false)
    }

    /** An Enter (`breakMessage` false) or a completion (true). */
    private fun send(
        state: ComposerState,
        breakMessage: Boolean,
    ): ComposerStep {
        if (state.inFlight != null) {
            val queued = (state.queuedBreak ?: false) || breakMessage
            return ComposerStep(state.copy(queuedBreak = queued))
        }
        val body = state.text.trimEnd()
        val nothingNew = body == state.sent.trimEnd()
        val nothingOpen = state.openMessageId == null && state.sent.isEmpty()
        if (body.isEmpty() && nothingOpen) return ComposerStep(state)
        if (nothingNew && !breakMessage) return ComposerStep(state)
        val commit = Commit(bodyMd = body, breakMessage = breakMessage)
        return ComposerStep(
            state.copy(inFlight = commit, error = null),
            listOf(ComposerEffect.Send(commit)),
        )
    }

    private fun answered(
        state: ComposerState,
        attempt: Commit,
        result: CommitResult,
    ): ComposerStep {
        val message = result.message
        val stored = message?.bodyMd ?: attempt.bodyMd
        // What was typed while the commit was on its way; null when the text sent was changed.
        val typedSince = typedSince(state.text, attempt.bodyMd)
        val closed = attempt.breakMessage || result.composer.openMessageId == null
        val next =
            if (closed) {
                state.copy(
                    text = typedSince?.trimStart() ?: state.text,
                    sent = "",
                    openMessageId = null,
                    typingSent = false,
                    justSent = null,
                    committedId = message?.id.takeIf { attempt.breakMessage },
                    openedAt = null,
                )
            } else {
                val text =
                    when {
                        typedSince == null -> state.text
                        typedSince.isEmpty() -> "$stored "
                        else -> stored + typedSince
                    }
                state.copy(
                    text = text,
                    sent = stored,
                    openMessageId = result.composer.openMessageId,
                    justSent = glowRange(state.sent, stored),
                    committedId = null,
                    openedAt = message?.openedAt ?: state.openedAt,
                )
            }
        return flushQueued(next.copy(server = result.composer, inFlight = null, error = null))
    }

    private fun failed(
        state: ComposerState,
        problem: ApiProblem,
    ): ComposerStep {
        val error =
            when (problem) {
                is ApiProblem.Server -> ComposerError.Refused(problem.error, problem.retryAfter)
                is ApiProblem.Network, is ApiProblem.Malformed -> ComposerError.Unreachable
            }
        // Nothing typed is ever lost (invariant 4): the text stays, unsent.
        return ComposerStep(state.copy(inFlight = null, queuedBreak = null, error = error))
    }

    private fun server(
        state: ComposerState,
        composer: ServerComposer,
        open: Message?,
    ): ComposerStep {
        val body = open?.bodyMd
        val resumed =
            if (body != null && composer.openMessageId == open.id && state.nothingSent) {
                val typed = state.text.trim()
                state.copy(
                    text = if (typed.isEmpty()) "$body " else "$body $typed",
                    sent = body,
                    openMessageId = open.id,
                    typingSent = true,
                    openedAt = open.openedAt,
                )
            } else {
                state
            }
        return ComposerStep(resumed.copy(server = composer))
    }

    private fun flushQueued(state: ComposerState): ComposerStep {
        val queued = state.queuedBreak ?: return ComposerStep(state)
        return send(state.copy(queuedBreak = null), queued)
    }
}

/** What was typed after [body] while it was on its way; null when [body] was changed. */
private fun typedSince(
    text: String,
    body: String,
): String? = if (text.startsWith(body)) text.substring(body.length) else null

/** The part of [text] that differs from [sent] (CO-04), or null when nothing is unsent. */
fun unsentRange(
    sent: String,
    text: String,
): IntRange? {
    if (text.trimEnd() == sent.trimEnd()) return null
    if (sent.isEmpty()) return text.indices.takeIf { text.isNotBlank() }
    val prefix = text.commonPrefixWith(sent).length
    val room = minOf(text.length, sent.length) - prefix
    val suffix =
        text
            .drop(prefix)
            .commonSuffixWith(sent.drop(prefix))
            .length
            .coerceAtMost(room)
    return prefix until text.length - suffix
}

/** What [stored] adds to [before]: the text just sent, which glows (CO-04). */
private fun glowRange(
    before: String,
    stored: String,
): IntRange? {
    val start = stored.commonPrefixWith(before).length
    return (start until stored.length).takeUnless { it.isEmpty() }
}

/**
 * The soft keyboard's return (CO-03): when [after] is [before] with one newline typed, the
 * text without it, which is an Enter, never a line break. Null otherwise.
 */
fun softReturn(
    before: String,
    after: String,
): String? {
    if (after.length != before.length + 1) return null
    val at = after.commonPrefixWith(before).length
    if (after[at] != '\n') return null
    val without = after.removeRange(at, at + 1)
    return without.takeIf { it == before }
}
