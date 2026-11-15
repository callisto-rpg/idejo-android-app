// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import org.idejo.app.core.model.Commit
import org.idejo.app.core.model.Message

/**
 * Reply, forward and edit (CO-16 to CO-18): the composer's transitions around a message,
 * as the web's (idejo `TopicLive.reply/3`, Forward, Edit).
 */
@Suppress("TooManyFunctions") // One for each transition.
internal object ComposerModes {
    fun reduce(
        state: ComposerState,
        event: ComposerEvent,
    ): ComposerStep =
        when (event) {
            is ComposerEvent.Reply -> reply(state, event)
            ComposerEvent.CancelReply -> cancel(state, state.copy(replying = null))
            is ComposerEvent.Forward -> begin(state, NewMessage(forwarding = event.forwarding))
            ComposerEvent.CancelForward -> cancel(state, state.copy(forwarding = null))
            ComposerEvent.AppendToOpen -> ComposerStep(appendToOpen(state))
            is ComposerEvent.Edit -> ComposerStep(edit(state, event.message))
            is ComposerEvent.Edited -> ComposerStep(edited(state, event.message, event.finish))
            ComposerEvent.ConfirmDelete -> confirmDelete(state)
            ComposerEvent.CancelDelete -> ComposerStep(state.copy(confirmDelete = false))
            ComposerEvent.Deleted -> ComposerStep(endEdit(state))
            else -> ComposerStep(state)
        }

    /**
     * Reply (CO-16): on a message above the open one, while editing, or in a new message
     * already replying or forwarding, its quote goes at the end of the text, inline; below
     * the open one, what is not sent goes first, then a new message replies; with nothing
     * being written, the composer replies.
     */
    private fun reply(
        state: ComposerState,
        event: ComposerEvent.Reply,
    ): ComposerStep {
        val writingNew =
            state.draftOver != null || state.replying != null || state.forwarding != null
        val open = state.openMessageId != null || state.sent.isNotEmpty()
        return when {
            state.editing != null || writingNew || (open && event.above) -> {
                ComposerStep(state.copy(text = withQuote(state.text, event.markdown)))
            }

            open -> {
                begin(state, NewMessage(replying = event.quote))
            }

            else -> {
                ComposerStep(state.copy(replying = event.quote))
            }
        }
    }

    /** A reply or forward: below the open message, once its unsent text went. */
    private fun begin(
        state: ComposerState,
        starting: NewMessage,
    ): ComposerStep {
        val open = state.openMessageId != null || state.sent.isNotEmpty()
        return when {
            state.editing != null -> {
                ComposerStep(state)
            }

            !open -> {
                ComposerStep(
                    state.copy(replying = starting.replying, forwarding = starting.forwarding),
                )
            }

            state.hasUnsent || state.inFlight != null -> {
                ComposerMachine.send(state.copy(queuedNew = starting), false)
            }

            else -> {
                ComposerStep(startNew(state, starting))
            }
        }
    }

    /** The open message stays; the composer is a new, empty message carrying [starting]. */
    fun startNew(
        state: ComposerState,
        starting: NewMessage,
    ): ComposerState {
        val open =
            state.openMessageId
                ?: return state.copy(replying = starting.replying, forwarding = starting.forwarding)
        return state.copy(
            text = "",
            sent = "",
            openMessageId = null,
            draftOver = Draft(open, state.sent),
            replying = starting.replying,
            forwarding = starting.forwarding,
            typingSent = true,
            justSent = null,
            openedAt = null,
        )
    }

    /** **Cancel reply** / **Cancel forward**: only before the first Enter. */
    private fun cancel(
        state: ComposerState,
        cancelled: ComposerState,
    ): ComposerStep {
        if (state.replying?.sent == true || (state.forwarding != null && state.sent.isNotEmpty())) {
            return ComposerStep(state)
        }
        val empty = cancelled.replying == null && cancelled.forwarding == null
        return ComposerStep(
            if (empty &&
                cancelled.draftOver != null
            ) {
                appendToOpen(cancelled)
            } else {
                cancelled
            },
        )
    }

    /** Back to the open message, keeping what was typed after it. */
    private fun appendToOpen(state: ComposerState): ComposerState {
        val draft = state.draftOver ?: return state.copy(error = null)
        val typed = state.text.trim()
        return state.copy(
            text = if (typed.isEmpty()) "${draft.sent} " else "${draft.sent} $typed",
            sent = draft.sent,
            openMessageId = draft.messageId,
            draftOver = null,
            replying = null,
            forwarding = null,
            error = null,
        )
    }

    /** **Edit** (CO-18): the composer goes into the message; the one before waits. */
    private fun edit(
        state: ComposerState,
        message: Message,
    ): ComposerState {
        if (state.editing != null || state.inFlight != null) return state
        val body = message.bodyMd.orEmpty()
        return ComposerState(
            text = body,
            sent = body,
            server = state.server,
            serverAtMillis = state.serverAtMillis,
            editing = Editing(message.id, state),
        )
    }

    /** Enter saves, Ctrl+Enter saves and ends the edit; emptied, Enter asks to delete. */
    fun save(
        state: ComposerState,
        finish: Boolean,
    ): ComposerStep {
        val editing = state.editing ?: return ComposerStep(state)
        val body = state.text.trimEnd()
        if (body.isEmpty()) return ComposerStep(state.copy(confirmDelete = true))
        if (body == state.sent.trimEnd() && !finish) return ComposerStep(state)
        return ComposerStep(
            state.copy(inFlight = Commit(body, breakMessage = finish), error = null),
            listOf(ComposerEffect.SaveEdit(editing.messageId, body, finish)),
        )
    }

    private fun edited(
        state: ComposerState,
        message: Message,
        finish: Boolean,
    ): ComposerState {
        if (finish) return endEdit(state)
        val stored = message.bodyMd.orEmpty()
        val sentBody = state.inFlight?.bodyMd.orEmpty()
        val typed = if (state.text.startsWith(sentBody)) state.text.drop(sentBody.length) else null
        return state.copy(
            text = if (typed == null) state.text else stored + typed,
            sent = stored,
            inFlight = null,
            justSent = null,
        )
    }

    private fun confirmDelete(state: ComposerState): ComposerStep {
        val editing = state.editing ?: return ComposerStep(state)
        return ComposerStep(
            state.copy(confirmDelete = false, inFlight = Commit("")),
            listOf(ComposerEffect.Delete(editing.messageId)),
        )
    }

    /** The edit is over: the composer is what it was before. */
    private fun endEdit(state: ComposerState): ComposerState =
        state.editing?.stash?.copy(server = state.server, serverAtMillis = state.serverAtMillis)
            ?: state.copy(inFlight = null)
}

/** [markdown] at the end of [text], parted by a blank line, and one after it. */
internal fun withQuote(
    text: String,
    markdown: String,
): String {
    val before = text.trimEnd()
    return (if (before.isEmpty()) "" else "$before\n\n") + markdown + "\n\n"
}
