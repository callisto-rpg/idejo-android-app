// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import org.idejo.app.core.data.TopicState
import org.idejo.app.core.model.Message

/**
 * The member's composer, which the app gives the page (features never depend on each other).
 * It stands in place of the author's open message, or at the end with none (CO-01).
 */
@Immutable
data class TopicWriting(
    /** The author's open message, or the one being edited: the composer is it (CO-18). */
    val openMessageId: String? = null,
    /** The server lets the member post here (`may_post`). */
    val mayWrite: Boolean = false,
    /** Ctrl+Enter anywhere on the page completes the message (CO-03). */
    val onComplete: () -> Unit = {},
    /** **Reply** on a message, true when it stands above the open message (CO-16). */
    val onReply: (Message, Boolean) -> Unit = { _, _ -> },
    /** **Forward**: the target card (CO-17). */
    val onForward: (Message) -> Unit = {},
    /** **Edit** one's closed message (CO-18). */
    val onEdit: (Message) -> Unit = {},
    val box: @Composable (Modifier) -> Unit = {},
)

/** With no open message in the window, the composer sits at the end, once reached (CO-01). */
internal fun composerAtEnd(
    topic: TopicState,
    writing: TopicWriting,
): Boolean =
    writing.mayWrite &&
        topic.atEnd &&
        topic.later == 0 &&
        topic.messages.none { it.id == writing.openMessageId }

/** Ctrl+Enter (or Cmd+Enter) outside the box completes the open message (CO-03). */
internal fun completes(
    event: KeyEvent,
    writing: TopicWriting,
): Boolean {
    val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
    val ctrl = event.isCtrlPressed || event.isMetaPressed
    if (!enter || !ctrl || !writing.mayWrite) return false
    if (event.type == KeyEventType.KeyDown) writing.onComplete()
    return true
}

/** Whether [message] stands above the author's open message in [topic] (CO-16). */
internal fun aboveOpen(
    topic: TopicState,
    message: Message,
    openMessageId: String?,
): Boolean {
    val open = topic.messages.indexOfFirst { it.id == openMessageId }
    val at = topic.messages.indexOfFirst { it.id == message.id }
    return open >= 0 && at in 0 until open
}
