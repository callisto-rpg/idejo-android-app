// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.flow.first
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.model.Conversation

/**
 * **Private Message** in a forum with a chat service (IB-07, IX-04): the conversation is the
 * inbox's, started there with the person's idejo ID (`POST /conversations` `{subject}`, with
 * the inbox's token), then the inbox opens at it in **Chats**.
 */
class InboxChats(
    private val sessions: SessionManager,
    private val links: PendingLinks,
) {
    /**
     * Starts or finds the conversation with idejo ID [subject] on the inbox and opens it.
     * Null outside idejo ID mode (no inbox held): the caller falls back to the web's link.
     */
    suspend fun open(subject: String): ApiResult<Conversation>? {
        val state = sessions.state.first()
        val inbox = state.inbox?.takeIf { state.mode == AppMode.IdejoId } ?: return null
        val result = sessions.signedIn(inbox.origin) { startConversationWith(subject) }
        if (result is ApiResult.Success) {
            links.offer(
                PendingLink(
                    inbox.origin,
                    inbox.origin.trimEnd('/') + "/p/" + result.value.id,
                ),
            )
            sessions.openInbox()
        }
        return result
    }

    /**
     * The inbox's conversations with something new (SH-13), read with its own token from any
     * forum; null outside idejo ID mode or when the inbox does not answer.
     */
    suspend fun withNews(): Int? {
        val state = sessions.state.first()
        val inbox = state.inbox?.takeIf { state.mode == AppMode.IdejoId } ?: return null
        val result = sessions.signedIn(inbox.origin) { conversations() }
        return (result as? ApiResult.Success)?.value?.count { it.unreadCount > 0 }
    }
}
