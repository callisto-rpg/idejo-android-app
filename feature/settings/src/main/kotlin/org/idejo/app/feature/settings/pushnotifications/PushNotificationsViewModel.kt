// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.pushnotifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
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
 * **Push notifications** (NT-08) on the forum open: the five types from
 * `GET /me/notification-settings` (a switch sends that type alone, `{"push": {…}}`), the
 * devices from `GET /me/push` (**Remove**: `DELETE /me/push/unifiedpush/{id}`), and
 * `POST /me/push/test`. Registering this device is NT-02's.
 */
@HiltViewModel
class PushNotificationsViewModel
    @Inject
    constructor(
        sessions: SessionManager,
    ) : ViewModel() {
        private val session = sessions.active
        private val mutableState = MutableStateFlow(PushNotificationsUiState())
        val state: StateFlow<PushNotificationsUiState> = mutableState.asStateFlow()

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

        fun onEvent(event: PushNotificationsEvent) {
            when (event) {
                is PushNotificationsEvent.Set -> change(event.choice, event.on)
                is PushNotificationsEvent.Remove -> remove(event.id)
                PushNotificationsEvent.SendTest -> sendTest()
                PushNotificationsEvent.Retry -> load()
            }
        }

        private fun load() {
            val forum = session
            if (forum == null) {
                mutableState.update { it.copy(loading = false) }
                return
            }
            mutableState.update { it.copy(loading = it.devices == null, loadProblem = null) }
            viewModelScope.launch {
                val settings = async { forum.call { notificationSettings() } }
                val devices = async { forum.call { pushRegistrations() } }
                val read = settings.await()
                val listed = devices.await()
                mutableState.update {
                    val failure =
                        (read as? ApiResult.Failure ?: listed as? ApiResult.Failure)?.problem
                    when {
                        failure == null -> {
                            it.copy(
                                loading = false,
                                push = (read as ApiResult.Success).value.push,
                                devices =
                                    (listed as ApiResult.Success).value.sortedByDescending { d ->
                                        d.thisDevice
                                    },
                            )
                        }

                        // Shown already: keep showing it.
                        it.devices != null -> {
                            it.copy(loading = false)
                        }

                        else -> {
                            it.copy(loading = false, loadProblem = failure)
                        }
                    }
                }
            }
        }

        /** Shows [on] at once, sends that type alone, and goes back on failure. */
        private fun change(
            choice: PushChoice,
            on: Boolean,
        ) {
            val forum = session ?: return
            val before = mutableState.value
            val push = before.push ?: return
            if (before.saving) return
            mutableState.value =
                before.copy(push = push.with(choice, on), saving = true, problem = null)
            val patch = NotificationSettings(push = NotificationSettings.Push().with(choice, on))
            viewModelScope.launch {
                when (val result = forum.call { updateNotificationSettings(patch) }) {
                    is ApiResult.Success -> {
                        mutableState.update {
                            it.copy(push = result.value.push ?: it.push, saving = false)
                        }
                    }

                    is ApiResult.Failure -> {
                        mutableState.update {
                            it.copy(push = push, saving = false, problem = result.problem)
                        }
                    }
                }
            }
        }

        private fun remove(id: String) {
            val forum = session ?: return
            if (mutableState.value.removing != null) return
            mutableState.update { it.copy(removing = id, problem = null) }
            viewModelScope.launch {
                val result = forum.call { deletePushRegistration(id) }
                mutableState.update {
                    when (result) {
                        is ApiResult.Success -> {
                            it.copy(
                                removing = null,
                                devices = it.devices?.filterNot { device -> device.id == id },
                            )
                        }

                        is ApiResult.Failure -> {
                            it.copy(removing = null, problem = result.problem)
                        }
                    }
                }
            }
        }

        private fun sendTest() {
            val forum = session ?: return
            if (mutableState.value.testing) return
            mutableState.update { it.copy(testing = true, tested = null, problem = null) }
            viewModelScope.launch {
                val result = forum.call { testPush() }
                mutableState.update {
                    when (result) {
                        is ApiResult.Success -> {
                            it.copy(
                                testing = false,
                                tested =
                                    if (result.value.queued > 0) {
                                        TestResult.Sent
                                    } else {
                                        TestResult.NoDevice
                                    },
                            )
                        }

                        // The server's words: "Too many tests…", push turned off…
                        is ApiResult.Failure -> {
                            it.copy(testing = false, problem = result.problem)
                        }
                    }
                }
            }
        }
    }
