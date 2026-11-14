// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import org.idejo.app.core.api.DiscoveryClient
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.data.SignInCoordinator
import org.idejo.app.core.data.SignInState
import javax.inject.Inject

/** What the add-forum screen shows (SI-01, SI-02). */
sealed interface AddForumState {
    data class Typing(
        val problem: Problem? = null,
    ) : AddForumState

    data object Looking : AddForumState

    data class Found(
        val result: DiscoveryResult.Found,
    ) : AddForumState

    sealed interface Problem {
        data class Address(
            val problem: ForumAddress.Problem,
        ) : Problem

        data object NotIdejo : Problem

        data class AppTurnedOff(
            val name: String,
        ) : Problem

        data object NeedsNewerApp : Problem

        data object Unreachable : Problem
    }
}

@HiltViewModel
class AddForumViewModel
    @Inject
    constructor(
        private val discovery: DiscoveryClient,
        private val sessions: SessionManager,
        private val signIn: SignInCoordinator,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow<AddForumState>(AddForumState.Typing())
        val state: StateFlow<AddForumState> = mutableState.asStateFlow()

        val signInState: StateFlow<SignInState> = signIn.state

        private var looking: Job? = null

        /** SI-01: what the member typed, then discovery. */
        fun lookUp(typed: String) {
            val address =
                when (val parsed = ForumAddress.parse(typed)) {
                    is ForumAddress.Parsed.Invalid -> {
                        mutableState.value =
                            AddForumState.Typing(AddForumState.Problem.Address(parsed.problem))
                        return
                    }

                    is ForumAddress.Parsed.Valid -> {
                        parsed.address
                    }
                }
            looking?.cancel()
            mutableState.value = AddForumState.Looking
            looking =
                viewModelScope.launch {
                    mutableState.value =
                        when (val result = discovery.discover(address)) {
                            is DiscoveryResult.Found -> {
                                AddForumState.Found(result)
                            }

                            DiscoveryResult.NotIdejo -> {
                                AddForumState.Typing(AddForumState.Problem.NotIdejo)
                            }

                            is DiscoveryResult.AppTurnedOff -> {
                                AddForumState.Typing(
                                    AddForumState.Problem.AppTurnedOff(result.name),
                                )
                            }

                            is DiscoveryResult.NeedsNewerApp -> {
                                AddForumState.Typing(AddForumState.Problem.NeedsNewerApp)
                            }

                            is DiscoveryResult.Unreachable -> {
                                AddForumState.Typing(AddForumState.Problem.Unreachable)
                            }
                        }
                }
        }

        /** Back to the address field. */
        fun edit() {
            looking?.cancel()
            mutableState.value = AddForumState.Typing()
        }

        /** SI-03: the page to open in a Custom Tab, or null (the failure is in [signInState]). */
        fun signIn(): HttpUrl? =
            (state.value as? AddForumState.Found)?.let {
                signIn.start(it.result, deviceName)
            }

        /** SI-05: the forum, without a token. */
        fun visit() {
            val found = (state.value as? AddForumState.Found)?.result ?: return
            viewModelScope.launch { sessions.add(found) }
        }

        fun tabClosed() = signIn.cancelIfWaiting()

        fun dismissFailure() = signIn.dismiss()
    }
