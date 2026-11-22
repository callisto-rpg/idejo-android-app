// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.data.Welcome
import org.idejo.app.core.data.WelcomeState
import javax.inject.Inject

/** Rules & Roles (BR-07) and the welcome card over the forum (BR-07). */
@HiltViewModel
class WelcomeViewModel
    @Inject
    constructor(
        sessions: SessionManager,
    ) : ViewModel() {
        private val welcome = sessions.active?.let { Welcome(it, viewModelScope) }
        val forumName: String = sessions.active?.name.orEmpty()
        val state: StateFlow<WelcomeState> =
            welcome?.state ?: MutableStateFlow(WelcomeState(loading = false))

        init {
            welcome?.refresh()
        }

        fun refresh() {
            welcome?.refresh()
        }

        fun tick(roleId: String) {
            welcome?.tick(roleId)
        }

        fun save() {
            welcome?.save()
        }

        fun continueWelcome() {
            welcome?.continueWelcome()
        }
    }
