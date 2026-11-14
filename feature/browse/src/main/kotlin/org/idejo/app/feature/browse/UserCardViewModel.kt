// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.model.UserCard
import javax.inject.Inject

/** A user card (BR-09). */
data class UserCardUi(
    val card: UserCard? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val problem: ApiProblem? = null,
    val origin: String = "",
)

/** A user card (BR-09): `GET /users/{id}`, and **Ignore** or **Stop ignoring** (AC-06). */
@HiltViewModel
class UserCardViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        sessions: SessionManager,
    ) : ViewModel() {
        private val id: String = checkNotNull(savedState["id"])
        private val session = sessions.active
        private val mutableState = MutableStateFlow(UserCardUi(origin = session?.origin.orEmpty()))
        val state: StateFlow<UserCardUi> = mutableState.asStateFlow()

        init {
            load()
        }

        fun load() {
            mutableState.update { it.copy(loading = true, problem = null) }
            viewModelScope.launch { done(session?.call { user(id) }) }
        }

        fun ignore(ignored: Boolean) {
            mutableState.update { it.copy(busy = true, problem = null) }
            viewModelScope.launch { done(session?.call { ignore(id, ignored) }) }
        }

        private fun done(result: ApiResult<UserCard>?) =
            mutableState.update {
                when (result) {
                    is ApiResult.Success -> {
                        it.copy(
                            card = result.value,
                            loading = false,
                            busy = false,
                        )
                    }

                    is ApiResult.Failure -> {
                        it.copy(
                            loading = false,
                            busy = false,
                            problem = result.problem,
                        )
                    }

                    null -> {
                        it.copy(loading = false, busy = false)
                    }
                }
            }
    }
