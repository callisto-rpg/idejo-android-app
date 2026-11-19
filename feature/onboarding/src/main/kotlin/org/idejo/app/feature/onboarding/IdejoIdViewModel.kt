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
import org.idejo.app.core.data.SignInCoordinator
import org.idejo.app.core.data.SignInState
import javax.inject.Inject

/**
 * **Sign in with idejo ID** (SI-06): the group's inbox, `my.idejo.org` unless the member
 * types another group's under "Use another idejo group" (MD-02), found by discovery, then
 * its sign-in page in a Custom Tab as any forum's (SI-03).
 */
@HiltViewModel
class IdejoIdViewModel
    @Inject
    constructor(
        private val discovery: DiscoveryClient,
        private val signIn: SignInCoordinator,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow<AddForumState>(AddForumState.Typing())

        /** Typing (with why the address failed), or Looking; Found starts the sign-in. */
        val state: StateFlow<AddForumState> = mutableState.asStateFlow()

        val signInState: StateFlow<SignInState> = signIn.state

        private val mutableTab = MutableStateFlow<HttpUrl?>(null)

        /** The inbox's sign-in page, to open in a Custom Tab once. */
        val tab: StateFlow<HttpUrl?> = mutableTab.asStateFlow()

        private var looking: Job? = null

        fun signIn(inbox: String) {
            looking?.cancel()
            looking =
                viewModelScope.launch {
                    mutableState.value = AddForumState.Looking
                    when (val found = discover(discovery, inbox)) {
                        is AddForumState.Found -> {
                            mutableState.value = AddForumState.Typing()
                            mutableTab.value = signIn.startInbox(found.result, deviceName)
                        }

                        else -> {
                            mutableState.value = found
                        }
                    }
                }
        }

        fun tabOpened() {
            mutableTab.value = null
        }

        fun tabClosed() = signIn.cancelIfWaiting()

        fun dismissFailure() = signIn.dismiss()

        companion object {
            /** idejo ID's inbox (MD-02). */
            const val DEFAULT_INBOX = "my.idejo.org"
        }
    }
