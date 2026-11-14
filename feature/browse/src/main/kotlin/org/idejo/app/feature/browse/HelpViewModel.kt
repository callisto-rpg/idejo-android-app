// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.model.Server
import javax.inject.Inject

/** **Help** (BR-08): its commands and plugins come from `GET /server` (IX-08). */
@HiltViewModel
class HelpViewModel
    @Inject
    constructor(
        sessions: SessionManager,
    ) : ViewModel() {
        private val session = sessions.active
        val server: StateFlow<Server?> = session?.server ?: MutableStateFlow(null)

        init {
            viewModelScope.launch { session?.refreshServer() }
        }
    }
