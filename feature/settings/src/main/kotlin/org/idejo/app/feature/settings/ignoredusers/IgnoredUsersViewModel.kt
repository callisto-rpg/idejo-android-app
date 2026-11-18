// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.ignoredusers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.data.SessionManager
import javax.inject.Inject

/**
 * **Ignored users** (ST-08) on the forum open, from `GET /me/ignored`. **Stop ignoring**
 * sends `DELETE /users/{id}/ignore`, the user card's call: the row goes once the server
 * agrees, and a refusal keeps it with the server's words.
 */
@HiltViewModel
class IgnoredUsersViewModel
    @Inject
    constructor(
        sessions: SessionManager,
    ) : ViewModel() {
        private val session = sessions.active
        private val mutableState =
            MutableStateFlow(IgnoredUsersUiState(origin = session?.origin.orEmpty()))
        val state: StateFlow<IgnoredUsersUiState> = mutableState.asStateFlow()

        init {
            load()
        }

        fun onEvent(event: IgnoredUsersEvent) {
            when (event) {
                is IgnoredUsersEvent.StopIgnoring -> stopIgnoring(event.id)
                IgnoredUsersEvent.Retry -> load()
            }
        }

        private fun load() {
            val forum = session
            if (forum == null) {
                mutableState.update { it.copy(loading = false) }
                return
            }
            mutableState.update { it.copy(loading = it.members == null, loadProblem = null) }
            viewModelScope.launch {
                val result = forum.call { ignored() }
                mutableState.update {
                    when {
                        result is ApiResult.Success -> {
                            it.copy(loading = false, members = result.value)
                        }

                        // Shown already: keep showing it.
                        result is ApiResult.Failure && it.members == null -> {
                            it.copy(loading = false, loadProblem = result.problem)
                        }

                        else -> {
                            it.copy(loading = false)
                        }
                    }
                }
            }
        }

        private fun stopIgnoring(id: String) {
            val forum = session ?: return
            if (mutableState.value.saving != null) return
            mutableState.update { it.copy(saving = id, problem = null) }
            viewModelScope.launch {
                val result = forum.call { ignore(id, ignored = false) }
                mutableState.update {
                    when (result) {
                        is ApiResult.Success -> {
                            it.copy(
                                saving = null,
                                members = it.members?.filterNot { person -> person.id == id },
                            )
                        }

                        is ApiResult.Failure -> {
                            it.copy(saving = null, problem = result.problem)
                        }
                    }
                }
            }
        }
    }
