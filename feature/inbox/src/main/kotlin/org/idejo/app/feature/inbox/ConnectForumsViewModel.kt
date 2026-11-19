// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.data.GroupForum
import org.idejo.app.core.data.GroupForums
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
}

@HiltViewModel
class ConnectForumsViewModel
    @Inject
    constructor(
        private val sessions: SessionManager,
        private val group: GroupForums,
        private val signIn: SignInCoordinator,
    ) : ViewModel() {
        private val mutableState =
            MutableStateFlow(ConnectForumsState(inboxName = sessions.inbox?.name.orEmpty()))
        val state: StateFlow<ConnectForumsState> = mutableState.asStateFlow()

        private val mutableTab = MutableStateFlow<HttpUrl?>(null)

        /** The sign-in page to open in a Custom Tab; [tabOpened] says whether it did. */
        val tab: StateFlow<HttpUrl?> = mutableTab.asStateFlow()
        private var opened: CompletableDeferred<Boolean>? = null
        private var connecting: Job? = null

        init {
            refresh()
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
            }
        }

        fun tabOpened(shown: Boolean) {
            mutableTab.value = null
            opened?.complete(shown)
        }

        /** Back from the sign-in page without its answer: the member closed it (SI-06). */
        fun tabClosed() = signIn.cancelIfWaiting()

        private fun refresh() {
            viewModelScope.launch { read() }
        }

        /** The list read again; forums new since last seen keep their card for this visit. */
        private suspend fun read() {
            mutableState.update { it.copy(loading = it.forums.isEmpty(), failed = false) }
            when (val result = group.refresh()) {
                is ApiResult.Success -> {
                    val fresh = result.value.filter { it.new }.map { it.url }
                    mutableState.update {
                        it.copy(forums = result.value, loading = false, fresh = it.fresh + fresh)
                    }
                    group.seen()
                }

                is ApiResult.Failure -> {
                    mutableState.update { it.copy(loading = false, failed = it.forums.isEmpty()) }
                }
            }
        }

        /** One forum after the other; one skipped or failed stays "Not connected". */
        private fun connect(forums: List<GroupForum>) {
            if (connecting?.isActive == true) return
            connecting =
                viewModelScope.launch {
                    for (forum in forums) {
                        mutableState.update { it.copy(connecting = forum.url) }
                        if (group.connect(forum, ::show)) {
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
                }
        }

        private suspend fun show(url: HttpUrl): Boolean {
            val shown = CompletableDeferred<Boolean>()
            opened = shown
            mutableTab.value = url
            return shown.await()
        }
    }
