// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.model.Conversation
import org.idejo.app.core.realtime.IdejoEvent

/** The member's private conversations (PC-01), as the sidebar and their page show them. */
data class ConversationsState(
    /** False on a forum without private messages, or for a visitor. */
    val available: Boolean = false,
    val conversations: List<Conversation> = emptyList(),
    val loading: Boolean = true,
    val problem: ApiProblem? = null,
) {
    /** The sidebar's number: conversations with something new (the member guide's). */
    val withNews: Int get() = conversations.count { it.unreadCount > 0 }

    fun named(id: String): Conversation? = conversations.firstOrNull { it.id == id }
}

/**
 * `GET /conversations` for one forum, read again when `user:me` says one changed
 * (`conversation.updated`). Only where the forum offers private messages (discovery's
 * `private_messages`) and the member is signed in; the token has the `private` scope.
 */
class ForumConversations(
    private val session: ForumSession,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(ConversationsState())
    val state: StateFlow<ConversationsState> = mutableState.asStateFlow()

    private var listening: Job? = null
    private var pending: Job? = null

    private val available: Boolean
        get() =
            session.access.value == Access.SignedIn &&
                PRIVATE_MESSAGES in session.record.value.discovery.features

    /** Loads the list and keeps it current; [close] stops. */
    fun start() {
        refresh()
        if (listening != null) return
        listening =
            scope.launch {
                session.userEvents.collect { event ->
                    if (event is IdejoEvent.ConversationUpdated) refreshSoon()
                }
            }
    }

    fun refresh() {
        scope.launch { load() }
    }

    fun close() {
        listening?.cancel()
        listening = null
    }

    private suspend fun load() {
        if (!available) {
            mutableState.value = ConversationsState(available = false, loading = false)
            return
        }
        mutableState.update { it.copy(available = true, loading = true) }
        when (val result = session.call { conversations() }) {
            is ApiResult.Success -> {
                mutableState.value =
                    ConversationsState(true, result.value, loading = false, problem = null)
            }

            is ApiResult.Failure -> {
                mutableState.update { it.copy(loading = false, problem = result.problem) }
            }
        }
    }

    /** Once a burst of events is over. */
    private fun refreshSoon() {
        pending?.cancel()
        pending =
            scope.launch {
                delay(SETTLE_MILLIS)
                load()
            }
    }

    private companion object {
        const val PRIVATE_MESSAGES = "private_messages"
        const val SETTLE_MILLIS = 300L
    }
}
