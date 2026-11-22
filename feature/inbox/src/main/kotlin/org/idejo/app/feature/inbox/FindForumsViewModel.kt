// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.data.GroupForums
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.data.SignInCoordinator
import org.idejo.app.core.model.DirectoryForum
import javax.inject.Inject

/** **Find more forums** (SI-08). */
data class FindForumsState(
    val query: String = "",
    val forums: List<DirectoryForum> = emptyList(),
    val page: Int = 0,
    val more: Boolean = false,
    val loading: Boolean = true,
    val failed: Boolean = false,
    /** Hosts of the forums the app holds a token for: **Open**, not **Join**. */
    val held: Set<String> = emptySet(),
    /** The forum whose sign-in page is open, while one is. */
    val joining: String? = null,
)

sealed interface FindForumsEvent {
    data class Search(
        val query: String,
    ) : FindForumsEvent

    data object More : FindForumsEvent

    data class Join(
        val forum: DirectoryForum,
    ) : FindForumsEvent

    data class Open(
        val forum: DirectoryForum,
    ) : FindForumsEvent

    data object TryAgain : FindForumsEvent
}

@HiltViewModel
class FindForumsViewModel
    @Inject
    constructor(
        private val sessions: SessionManager,
        private val group: GroupForums,
        private val signIn: SignInCoordinator,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(FindForumsState())
        val state: StateFlow<FindForumsState> = mutableState.asStateFlow()

        val tabs = SignInTabs()
        private var reading: Job? = null

        init {
            read(page = 1, wait = false)
            viewModelScope.launch {
                group.joined.collect { joined ->
                    val held = joined.filter { it.connected }.mapNotNull { hostOf(it.url) }
                    mutableState.update { it.copy(held = held.toSet()) }
                }
            }
        }

        fun onEvent(event: FindForumsEvent) {
            when (event) {
                is FindForumsEvent.Search -> {
                    mutableState.update { it.copy(query = event.query) }
                    read(page = 1, wait = true)
                }

                FindForumsEvent.More -> {
                    read(page = state.value.page + 1, wait = false)
                }

                FindForumsEvent.TryAgain -> {
                    read(page = maxOf(1, state.value.page), wait = false)
                }

                is FindForumsEvent.Join -> {
                    join(event.forum)
                }

                is FindForumsEvent.Open -> {
                    open(event.forum)
                }
            }
        }

        /** Back from the sign-in page without its answer (SI-08). */
        fun tabClosed() = signIn.cancelIfWaiting()

        /** One read at a time; typing waits a moment, as the directory counts requests. */
        private fun read(
            page: Int,
            wait: Boolean,
        ) {
            reading?.cancel()
            reading =
                viewModelScope.launch {
                    if (wait) delay(TYPING_MS)
                    mutableState.update { it.copy(loading = true, failed = false) }
                    when (val result = group.directory(state.value.query, page)) {
                        is ApiResult.Success -> {
                            val found = result.value
                            mutableState.update {
                                it.copy(
                                    forums =
                                        if (page ==
                                            1
                                        ) {
                                            found.forums
                                        } else {
                                            it.forums + found.forums
                                        },
                                    page = found.page,
                                    more = found.more,
                                    loading = false,
                                )
                            }
                        }

                        is ApiResult.Failure -> {
                            mutableState.update { it.copy(loading = false, failed = true) }
                        }
                    }
                }
        }

        private fun join(forum: DirectoryForum) {
            if (state.value.joining != null) return
            viewModelScope.launch {
                mutableState.update { it.copy(joining = forum.host) }
                val joined = group.join(forum, tabs::show)
                mutableState.update { it.copy(joining = null) }
                // Joined: the forum opens here, in the app, as Open does.
                joined?.let { sessions.enter(it) }
            }
        }

        private fun open(forum: DirectoryForum) {
            val held =
                group.joined.value.firstOrNull {
                    it.connected && hostOf(
                        it.url,
                    ) == forum.host
                }
            viewModelScope.launch { held?.let { sessions.enter(it.url) } }
        }

        private companion object {
            const val TYPING_MS = 400L

            fun hostOf(url: String): String? = url.toHttpUrlOrNull()?.host
        }
    }
