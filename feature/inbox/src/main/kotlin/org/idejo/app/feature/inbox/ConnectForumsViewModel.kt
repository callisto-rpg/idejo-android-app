// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.data.GroupForum
import org.idejo.app.core.data.GroupForums
import org.idejo.app.core.data.InboxShortcut
import org.idejo.app.core.data.InboxShortcuts
import org.idejo.app.core.data.InboxTopics
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.data.SignInCoordinator
import javax.inject.Inject

/** **Connect your forums** (SI-06). */
data class ConnectForumsState(
    /** The inbox's name: idejo's own group, or another. */
    val inboxName: String = "",
    val forums: List<GroupForum> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    /** The forum whose sign-in page is open, while one is. */
    val connecting: String? = null,
    /** Joined since last seen (SI-07): a Connect card each, for this visit. */
    val fresh: Set<String> = emptySet(),
    /** The group has a directory: **Find more forums** (SI-08). */
    val directory: Boolean = false,
    /** Each connected forum's new messages (IB-02), by origin. */
    val unread: Map<String, Int> = emptyMap(),
    /**
     * The inbox answers the cards' long-press menu, **Add to favourites** and **Leave
     * forum** (idejo 0.3.28, discovery's `forum_menu`; steward request 2026-11-24).
     */
    val menu: Boolean = false,
    /** The forum whose **Leave forum** waits for the member's answer. */
    val leaving: GroupForum? = null,
    /** The last favourite or leave the inbox did not take: "That did not work…". */
    val actionFailed: Boolean = false,
) {
    val notConnected: List<GroupForum> get() = forums.filterNot { it.connected }

    /** The Connect cards: forums joined since last seen, not connected yet (SI-07). */
    val cards: List<GroupForum> get() = notConnected.filter { it.url in fresh }
}

sealed interface ConnectForumsEvent {
    /** **Connect N forums**: each forum not connected, one sign-in page after the other. */
    data object ConnectAll : ConnectForumsEvent

    data class Connect(
        val forum: GroupForum,
    ) : ConnectForumsEvent

    /** A connected forum tapped: it opens (IB-02). */
    data class Enter(
        val forum: GroupForum,
    ) : ConnectForumsEvent

    data object Refresh : ConnectForumsEvent

    /** The card's menu: **Add to favourites** or **Remove from favourites**. */
    data class Favourite(
        val forum: GroupForum,
        val favourite: Boolean,
    ) : ConnectForumsEvent

    /** The card's menu: **Leave forum**, which asks first. */
    data class Leave(
        val forum: GroupForum,
    ) : ConnectForumsEvent

    /** The member's answer to "Leave {name}?". */
    data class ConfirmLeave(
        val leave: Boolean,
    ) : ConnectForumsEvent
}

@HiltViewModel
class ConnectForumsViewModel
    @Inject
    constructor(
        private val sessions: SessionManager,
        private val group: GroupForums,
        private val signIn: SignInCoordinator,
        private val topics: InboxTopics,
        private val shortcuts: InboxShortcuts,
    ) : ViewModel() {
        private val mutableState =
            MutableStateFlow(ConnectForumsState(inboxName = sessions.inbox?.name.orEmpty()))
        val state: StateFlow<ConnectForumsState> = mutableState.asStateFlow()

        /** The sign-in pages to open in a Custom Tab, one at a time. */
        val tabs = SignInTabs()
        private var connecting: Job? = null

        init {
            refresh()
            viewModelScope.launch {
                topics.state.collect { read ->
                    mutableState.update { it.copy(unread = read.unread) }
                }
            }
            // **Connect** in the inbox's own forum menu, while this tab is shown (SH-12).
            viewModelScope.launch {
                shortcuts.waiting.filterIsInstance<InboxShortcut.Connect>().collect {
                    connectAsked(state.value.forums)
                }
            }
        }

        fun onEvent(event: ConnectForumsEvent) {
            when (event) {
                ConnectForumsEvent.ConnectAll -> {
                    connect(state.value.notConnected)
                }

                is ConnectForumsEvent.Connect -> {
                    connect(listOf(event.forum))
                }

                is ConnectForumsEvent.Enter -> {
                    viewModelScope.launch {
                        sessions.enter(
                            event.forum.url,
                        )
                    }
                }

                ConnectForumsEvent.Refresh -> {
                    refresh()
                }

                is ConnectForumsEvent.Favourite -> {
                    favourite(event.forum, event.favourite)
                }

                is ConnectForumsEvent.Leave -> {
                    mutableState.update { it.copy(leaving = event.forum, actionFailed = false) }
                }

                is ConnectForumsEvent.ConfirmLeave -> {
                    leave(event.leave)
                }
            }
        }

        private fun favourite(
            forum: GroupForum,
            favourite: Boolean,
        ) {
            viewModelScope.launch {
                mutableState.update { it.copy(actionFailed = false) }
                val done = group.favourite(forum, favourite)
                mutableState.update { state ->
                    state.copy(
                        forums =
                            if (done) {
                                state.forums.map {
                                    if (it.url == forum.url) it.copy(favourite = favourite) else it
                                }
                            } else {
                                state.forums
                            },
                        actionFailed = !done,
                    )
                }
            }
        }

        private fun leave(confirmed: Boolean) {
            val forum = state.value.leaving ?: return
            mutableState.update { it.copy(leaving = null) }
            if (!confirmed) return
            viewModelScope.launch {
                val done = group.leave(forum)
                mutableState.update { state ->
                    state.copy(
                        forums =
                            if (done) {
                                state.forums.filterNot {
                                    it.url == forum.url
                                }
                            } else {
                                state.forums
                            },
                        actionFailed = !done,
                    )
                }
                if (done) topics.refresh()
            }
        }

        /** Back from the sign-in page without its answer: the member closed it (SI-06). */
        fun tabClosed() = signIn.cancelIfWaiting()

        private fun refresh() {
            viewModelScope.launch { read() }
        }

        /** The list read again; forums new since last seen keep their card for this visit. */
        private suspend fun read() {
            mutableState.update { it.copy(loading = it.forums.isEmpty(), failed = false) }
            val directory = group.hasDirectory()
            val menu = group.hasMenu()
            when (val result = group.refresh()) {
                is ApiResult.Success -> {
                    val fresh = result.value.filter { it.new }.map { it.url }
                    mutableState.update {
                        it.copy(
                            forums = result.value,
                            loading = false,
                            fresh = it.fresh + fresh,
                            directory = directory,
                            menu = menu,
                        )
                    }
                    group.seen()
                    connectAsked(result.value)
                }

                is ApiResult.Failure -> {
                    mutableState.update { it.copy(loading = false, failed = it.forums.isEmpty()) }
                }
            }
        }

        /** **Connect** beside a forum in the logo menu (SH-12): its sign-in, here. */
        private fun connectAsked(forums: List<GroupForum>) {
            // Until the forums are read, it waits for them.
            if (forums.isEmpty()) return
            val asked = shortcuts.take { it is InboxShortcut.Connect } as? InboxShortcut.Connect
            val forum = forums.firstOrNull { it.url == asked?.url } ?: return
            if (!forum.connected) connect(listOf(forum))
        }

        /** One forum after the other; one skipped or failed stays "Not connected". */
        private fun connect(forums: List<GroupForum>) {
            if (connecting?.isActive == true) return
            connecting =
                viewModelScope.launch {
                    for (forum in forums) {
                        mutableState.update { it.copy(connecting = forum.url) }
                        if (group.connect(forum, tabs::show) != null) {
                            mutableState.update { state ->
                                state.copy(
                                    forums =
                                        state.forums.map {
                                            if (it.url ==
                                                forum.url
                                            ) {
                                                it.copy(connected = true)
                                            } else {
                                                it
                                            }
                                        },
                                )
                            }
                        }
                    }
                    mutableState.update { it.copy(connecting = null) }
                    read()
                    topics.refresh()
                }
        }
    }
