// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.password

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
import org.idejo.app.core.model.PasswordChange
import javax.inject.Inject

/**
 * **Change password** (ST-04) on the forum open: `PUT /me/password`. The server checks the
 * passwords (its rules, its words). Once it agrees, every device of the account signs in
 * again, this one too: the next call meets `401 invalid_token`, and the forum shows "You were
 * signed out" with **Sign in** (SI-09).
 */
@HiltViewModel
class PasswordViewModel
    @Inject
    constructor(
        sessions: SessionManager,
    ) : ViewModel() {
        private val session = sessions.active
        private val mutableState = MutableStateFlow(PasswordUiState())
        val state: StateFlow<PasswordUiState> = mutableState.asStateFlow()

        fun onEvent(event: PasswordEvent) {
            when (event) {
                is PasswordEvent.EditCurrent -> {
                    mutableState.update {
                        it.copy(
                            current = event.text,
                        )
                    }
                }

                is PasswordEvent.EditNew -> {
                    mutableState.update { it.copy(new = event.text) }
                }

                is PasswordEvent.EditRepeat -> {
                    mutableState.update { it.copy(repeat = event.text) }
                }

                PasswordEvent.Change -> {
                    change()
                }
            }
        }

        private fun change() {
            val forum = session ?: return
            val typed = state.value
            if (typed.saving) return
            mutableState.update { it.copy(saving = true, problem = null) }
            viewModelScope.launch {
                val result =
                    forum.call {
                        changePassword(
                            PasswordChange(typed.current, typed.new, typed.repeat),
                        )
                    }
                when (result) {
                    is ApiResult.Success -> {
                        // Nothing typed is kept once it has served.
                        mutableState.value = PasswordUiState(changed = true)
                        // This device's token is revoked too: reading the member finds out
                        // and signs out here (SI-09), so the forum asks to sign in again.
                        forum.me.refresh()
                    }

                    is ApiResult.Failure -> {
                        mutableState.update { it.copy(saving = false, problem = result.problem) }
                    }
                }
            }
        }
    }
