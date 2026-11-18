// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.appsdevices

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
 * **Apps and devices** (ST-05) on the forum open, from `GET /me/tokens`. **Remove** sends
 * `DELETE /me/tokens/{id}` and the row goes once the server agrees; for this device it signs
 * out as **Sign out** does (SI-11: the token revoked and forgotten, the forum kept).
 */
@HiltViewModel
class AppsDevicesViewModel
    @Inject
    constructor(
        private val sessions: SessionManager,
    ) : ViewModel() {
        private val session = sessions.active
        private val mutableState = MutableStateFlow(AppsDevicesUiState())
        val state: StateFlow<AppsDevicesUiState> = mutableState.asStateFlow()

        init {
            load()
        }

        fun onEvent(event: AppsDevicesEvent) {
            when (event) {
                is AppsDevicesEvent.Remove -> remove(event.id)
                AppsDevicesEvent.Retry -> load()
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
                val result = forum.call { tokens() }
                mutableState.update {
                    when {
                        result is ApiResult.Success -> {
                            it.copy(
                                loading = false,
                                devices =
                                    result.value.sortedByDescending { device ->
                                        device.current
                                    },
                            )
                        }

                        // Shown already: keep showing it.
                        result is ApiResult.Failure && it.devices == null -> {
                            it.copy(loading = false, loadProblem = result.problem)
                        }

                        else -> {
                            it.copy(loading = false)
                        }
                    }
                }
            }
        }

        private fun remove(id: String) {
            val forum = session ?: return
            val before = mutableState.value
            if (before.removing != null) return
            val device = before.devices?.firstOrNull { it.id == id } ?: return
            mutableState.update { it.copy(removing = id, problem = null) }
            viewModelScope.launch {
                if (device.current) {
                    // The shell follows the forum's new state: signed out, as a visitor.
                    sessions.signOut(forum.origin)
                    return@launch
                }
                val result = forum.call { deleteToken(id) }
                mutableState.update {
                    when (result) {
                        is ApiResult.Success -> {
                            it.copy(
                                removing = null,
                                devices =
                                    it.devices?.filterNot { d ->
                                        d.id ==
                                            id
                                    },
                            )
                        }

                        is ApiResult.Failure -> {
                            it.copy(removing = null, problem = result.problem)
                        }
                    }
                }
            }
        }
    }
