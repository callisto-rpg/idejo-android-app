// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.writing

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
 * **Writing** (ST-02) on the forum open, from its one copy of `GET /me` (`MeRepository`).
 * A switch moves at once, `PATCH /me` sends only that field, and a refusal puts it back with
 * the server's words. The composer and message controls read the same copy, so the open
 * topic follows at once.
 */
@HiltViewModel
class WritingViewModel
    @Inject
    constructor(
        sessions: SessionManager,
    ) : ViewModel() {
        private val session = sessions.active
        private val mutableState = MutableStateFlow(WritingUiState())
        val state: StateFlow<WritingUiState> = mutableState.asStateFlow()

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

        fun onEvent(event: WritingEvent) {
            when (event) {
                is WritingEvent.AutoPunctuation -> {
                    change(
                        MePatch(autoPunctuation = event.on),
                    ) { it.copy(autoPunctuation = event.on) }
                }

                is WritingEvent.GraphicalComposer -> {
                    change(
                        MePatch(graphicalComposer = event.on),
                    ) { it.copy(graphicalComposer = event.on) }
                }

                WritingEvent.Retry -> {
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
            optimistic: (WritingUiState) -> WritingUiState,
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
        private fun WritingUiState.with(me: Me) =
            copy(autoPunctuation = me.autoPunctuation, graphicalComposer = me.graphicalComposer)
    }
