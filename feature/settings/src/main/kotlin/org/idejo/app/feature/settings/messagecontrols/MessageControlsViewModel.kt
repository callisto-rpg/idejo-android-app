// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.messagecontrols

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.model.Me
import org.idejo.app.core.model.MePatch
import javax.inject.Inject

/**
 * **Message controls** (ST-03, MA-01) on the forum open, from its one copy of `GET /me`
 * (`MeRepository`). A switch moves at once, `PATCH /me` sends only that field, and a refusal
 * puts it back with the server's words. The topic page reads the same copy, so open topics
 * follow at once.
 */
@HiltViewModel
class MessageControlsViewModel
    @Inject
    constructor(
        sessions: SessionManager,
    ) : ViewModel() {
        private val session = sessions.active
        private val mutableState = MutableStateFlow(MessageControlsUiState())
        val state: StateFlow<MessageControlsUiState> = mutableState.asStateFlow()

        init {
            session?.let { forum ->
                // Whatever the server last answered, whoever asked.
                viewModelScope.launch {
                    forum.me.state.filterNotNull().collect { me ->
                        mutableState.update {
                            it
                                .with(
                                    me,
                                ).copy(loading = false, loadProblem = null)
                        }
                    }
                }
            }
            load()
        }

        fun onEvent(event: MessageControlsEvent) {
            when (event) {
                is MessageControlsEvent.OnMobile -> {
                    change(MePatch(controlsOnMobile = event.on)) { it.copy(onMobile = event.on) }
                }

                is MessageControlsEvent.OnDesktop -> {
                    change(MePatch(controlsOnDesktop = event.on)) { it.copy(onDesktop = event.on) }
                }

                MessageControlsEvent.Retry -> {
                    load()
                }
            }
        }

        private fun load() {
            val forum = session
            if (forum == null) {
                mutableState.update { it.copy(loading = false) }
                return
            }
            // Cache first: the spinner only while nothing is known yet.
            mutableState.update {
                it.copy(
                    loading = forum.me.state.value == null,
                    loadProblem = null,
                )
            }
            viewModelScope.launch {
                val result = forum.me.refresh()
                mutableState.update {
                    when {
                        // The answer itself, not waiting for the collector above.
                        result is ApiResult.Success -> {
                            it.with(result.value).copy(loading = false)
                        }

                        // Shown already (the shell read it): keep showing it.
                        result is ApiResult.Failure && forum.me.state.value == null -> {
                            it.copy(loading = false, loadProblem = result.problem)
                        }

                        else -> {
                            it.copy(loading = false)
                        }
                    }
                }
            }
        }

        /** Shows [optimistic] at once, sends [patch], and goes back to how it was on failure. */
        private fun change(
            patch: MePatch,
            optimistic: (MessageControlsUiState) -> MessageControlsUiState,
        ) {
            val forum = session ?: return
            val before = mutableState.value
            mutableState.value = optimistic(before).copy(saving = true, problem = null)
            viewModelScope.launch {
                when (val result = forum.me.update(patch)) {
                    is ApiResult.Success -> {
                        mutableState.update { it.with(result.value).copy(saving = false) }
                    }

                    is ApiResult.Failure -> {
                        mutableState.value = before.copy(saving = false, problem = result.problem)
                    }
                }
            }
        }

        /** The server's answer, as this section shows it. */
        private fun MessageControlsUiState.with(me: Me) =
            copy(onMobile = me.controlsOnMobile, onDesktop = me.controlsOnDesktop)
    }
