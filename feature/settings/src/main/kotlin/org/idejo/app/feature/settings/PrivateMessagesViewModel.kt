// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.data.Access
import org.idejo.app.core.data.SessionManager

/** The private messages switch (PC-03): null [accepts] hides it (a visitor, no such forum). */
@Immutable
data class PrivateMessagesUi(
    val accepts: Boolean? = null,
    val saving: Boolean = false,
    /** Why the last change failed: the server's words, or offline. */
    val problem: String? = null,
)

/** "Members can send me private messages" on the forum open (PC-03): `GET` and `PATCH /me`. */
@HiltViewModel
class PrivateMessagesViewModel
    @javax.inject.Inject
    constructor(
        sessions: SessionManager,
        private val application: Application,
    ) : AndroidViewModel(application) {
        private val session = sessions.active
        private val mutableState = MutableStateFlow(PrivateMessagesUi())
        val state: StateFlow<PrivateMessagesUi> = mutableState.asStateFlow()

        init {
            val forum = session
            if (forum != null &&
                forum.access.value == Access.SignedIn &&
                PRIVATE_MESSAGES in forum.record.value.discovery.features
            ) {
                viewModelScope.launch {
                    val me = forum.call { me() }
                    if (me is ApiResult.Success) {
                        mutableState.value = PrivateMessagesUi(me.value.acceptsPrivateMessages)
                    }
                }
            }
        }

        fun accept(accepts: Boolean) {
            val forum = session ?: return
            mutableState.update { it.copy(accepts = accepts, saving = true, problem = null) }
            viewModelScope.launch {
                val result = forum.call { acceptPrivateMessages(accepts) }
                mutableState.update {
                    when (result) {
                        is ApiResult.Success -> {
                            PrivateMessagesUi(result.value.acceptsPrivateMessages)
                        }

                        is ApiResult.Failure -> {
                            it.copy(
                                accepts = !accepts,
                                saving = false,
                                problem = words(result.problem),
                            )
                        }
                    }
                }
            }
        }

        private fun words(problem: ApiProblem): String =
            when (problem) {
                is ApiProblem.Server -> problem.error.message
                else -> application.getString(R.string.private_messages_offline)
            }

        private companion object {
            const val PRIVATE_MESSAGES = "private_messages"
        }
    }
