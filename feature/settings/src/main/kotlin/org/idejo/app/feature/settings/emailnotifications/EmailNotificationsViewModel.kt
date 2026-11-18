// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.emailnotifications

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
import org.idejo.app.core.model.NotificationSettings
import javax.inject.Inject

/**
 * **Email notifications** (ST-03) on the forum open, from `GET /me/notification-settings`.
 * A switch moves at once, `PATCH /me/notification-settings` sends only that choice
 * (`{"email": {"<choice>": …}}`), and a refusal puts it back with the server's words. The
 * username for the first row comes from the forum's one copy of `GET /me`, which the shell
 * reads; until then the row reads without it.
 */
@HiltViewModel
class EmailNotificationsViewModel
    @Inject
    constructor(
        sessions: SessionManager,
    ) : ViewModel() {
        private val session = sessions.active
        private val mutableState = MutableStateFlow(EmailNotificationsUiState())
        val state: StateFlow<EmailNotificationsUiState> = mutableState.asStateFlow()

        init {
            session?.let { forum ->
                viewModelScope.launch {
                    forum.me.state.filterNotNull().collect { me ->
                        mutableState.update { it.copy(username = me.username) }
                    }
                }
            }
            load()
        }

        fun onEvent(event: EmailNotificationsEvent) {
            when (event) {
                is EmailNotificationsEvent.Set -> change(event.choice, event.on)
                EmailNotificationsEvent.Retry -> load()
            }
        }

        private fun load() {
            val forum = session
            if (forum == null) {
                mutableState.update { it.copy(loading = false) }
                return
            }
            mutableState.update { it.copy(loading = it.email == null, loadProblem = null) }
            viewModelScope.launch {
                val result = forum.call { notificationSettings() }
                mutableState.update {
                    when {
                        result is ApiResult.Success -> {
                            it.copy(loading = false, email = result.value.email)
                        }

                        // Shown already: keep showing it.
                        result is ApiResult.Failure && it.email == null -> {
                            it.copy(loading = false, loadProblem = result.problem)
                        }

                        else -> {
                            it.copy(loading = false)
                        }
                    }
                }
            }
        }

        /** Shows [on] at once, sends that choice alone, and goes back on failure. */
        private fun change(
            choice: EmailChoice,
            on: Boolean,
        ) {
            val forum = session ?: return
            val before = mutableState.value
            val email = before.email ?: return
            if (before.saving) return
            mutableState.value =
                before.copy(email = email.with(choice, on), saving = true, problem = null)
            val patch = NotificationSettings(email = NotificationSettings.Email().with(choice, on))
            viewModelScope.launch {
                when (val result = forum.call { updateNotificationSettings(patch) }) {
                    is ApiResult.Success -> {
                        mutableState.update {
                            it.copy(email = result.value.email ?: it.email, saving = false)
                        }
                    }

                    is ApiResult.Failure -> {
                        mutableState.update {
                            it.copy(email = email, saving = false, problem = result.problem)
                        }
                    }
                }
            }
        }
    }
