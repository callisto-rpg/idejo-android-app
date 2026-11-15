// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.idejo.app.core.data.ConversationsState
import org.idejo.app.core.data.ForumConversations
import org.idejo.app.core.data.SessionManager
import javax.inject.Inject

/** **Private Conversations** (PC-01): the list, read again as `user:me` reports changes. */
@HiltViewModel
class ConversationsViewModel
    @Inject
    constructor(
        sessions: SessionManager,
    ) : ViewModel() {
        private val session = sessions.active
        private val conversations = session?.let { ForumConversations(it, viewModelScope) }

        val origin: String = session?.origin.orEmpty()

        val state: StateFlow<ConversationsState> =
            conversations?.state ?: MutableStateFlow(ConversationsState(loading = false))

        init {
            conversations?.start()
        }

        fun refresh() {
            conversations?.refresh()
        }

        override fun onCleared() {
            conversations?.close()
        }
    }
