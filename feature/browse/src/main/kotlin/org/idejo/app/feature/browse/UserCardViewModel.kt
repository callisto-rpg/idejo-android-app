// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.data.AppMode
import org.idejo.app.core.data.InboxChats
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.model.UserCard
import javax.inject.Inject

/** A user card (BR-09). */
data class UserCardUi(
    val card: UserCard? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val problem: ApiProblem? = null,
    val origin: String = "",
    /** The private conversation **Private Message** started or found (PC-03). */
    val conversation: String? = null,
    /**
     * The forum's chat service (IX-04): **Private Message** then needs the person's idejo
     * ID, and the conversation is the inbox's (IB-07).
     */
    val chat: String? = null,
    /** Single-forum mode with a chat service: the web's link, for a Custom Tab (PC-02). */
    val outside: String? = null,
    /** Single-forum mode (MD-03): with a chat service, the card offers idejo ID mode. */
    val single: Boolean = false,
) {
    /** **Private Message** shows as on the web: by the idejo ID with a chat service. */
    val mayMessage: Boolean
        get() = card != null && if (chat != null) card.idejoId != null else card.mayMessage

    /**
     * PC-02: in single-forum mode, under a chat service's **Private Message**, the line
     * offering idejo ID mode, where those chats are the inbox's.
     */
    val offersIdejoId: Boolean get() = single && chat != null && mayMessage
}

/** A user card (BR-09): `GET /users/{id}`, and **Ignore** or **Stop ignoring** (AC-06). */
@HiltViewModel
class UserCardViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val sessions: SessionManager,
        private val chats: InboxChats,
    ) : ViewModel() {
        private val id: String = checkNotNull(savedState["id"])
        private val session = sessions.active
        private val mutableState =
            MutableStateFlow(
                UserCardUi(
                    origin = session?.origin.orEmpty(),
                    chat =
                        session
                            ?.record
                            ?.value
                            ?.discovery
                            ?.community
                            ?.chatUrl,
                ),
            )
        val state: StateFlow<UserCardUi> = mutableState.asStateFlow()

        init {
            load()
            viewModelScope.launch {
                val single = sessions.state.first().mode == AppMode.Single
                mutableState.update { it.copy(single = single) }
            }
        }

        fun load() {
            mutableState.update { it.copy(loading = true, problem = null) }
            viewModelScope.launch { done(session?.cached("user:$id") { user(id) }) }
        }

        fun ignore(ignored: Boolean) {
            mutableState.update { it.copy(busy = true, problem = null) }
            viewModelScope.launch { done(session?.call { ignore(id, ignored) }) }
        }

        /**
         * **Private Message** (PC-03): the conversation with them, started or found; with a
         * chat service, the inbox's (IB-07), or outside idejo ID mode the web's link (PC-02).
         */
        fun message() {
            val chat = state.value.chat
            val subject = state.value.card?.idejoId
            if (chat != null && subject != null) {
                messageThroughInbox(chat, subject)
                return
            }
            mutableState.update { it.copy(busy = true, problem = null) }
            viewModelScope.launch {
                val result = session?.call { startConversation(id) }
                mutableState.update {
                    when (result) {
                        is ApiResult.Success -> {
                            it.copy(
                                busy = false,
                                conversation = result.value.id,
                            )
                        }

                        is ApiResult.Failure -> {
                            it.copy(busy = false, problem = result.problem)
                        }

                        null -> {
                            it.copy(busy = false)
                        }
                    }
                }
            }
        }

        /** The link was opened outside the app. */
        fun outsideOpened() = mutableState.update { it.copy(outside = null) }

        private fun messageThroughInbox(
            chat: String,
            subject: String,
        ) {
            mutableState.update { it.copy(busy = true, problem = null) }
            viewModelScope.launch {
                val result = chats.open(subject)
                mutableState.update {
                    when (result) {
                        // The inbox opens at the conversation; this card goes with the forum.
                        is ApiResult.Success -> {
                            it.copy(busy = false)
                        }

                        is ApiResult.Failure -> {
                            it.copy(busy = false, problem = result.problem)
                        }

                        null -> {
                            it.copy(busy = false, outside = chatLink(chat, subject))
                        }
                    }
                }
            }
        }

        private fun done(result: ApiResult<UserCard>?) =
            mutableState.update {
                when (result) {
                    is ApiResult.Success -> {
                        it.copy(
                            card = result.value,
                            loading = false,
                            busy = false,
                        )
                    }

                    is ApiResult.Failure -> {
                        it.copy(
                            loading = false,
                            busy = false,
                            problem = result.problem,
                        )
                    }

                    null -> {
                        it.copy(loading = false, busy = false)
                    }
                }
            }
    }

/** The web's **Private Message** at the chat service: `<chat_url>/new?with=<idejo ID>`. */
internal fun chatLink(
    chat: String,
    subject: String,
): String = chat.trimEnd('/') + "/new?with=" + java.net.URLEncoder.encode(subject, "UTF-8")
