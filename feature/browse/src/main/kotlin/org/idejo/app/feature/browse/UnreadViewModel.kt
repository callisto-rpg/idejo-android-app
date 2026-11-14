// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.data.UnreadFeed
import org.idejo.app.core.data.UnreadState
import org.idejo.app.core.model.Category
import javax.inject.Inject

/** **Unread** (BR-06). */
data class UnreadUi(
    val unread: UnreadState = UnreadState(),
    val categories: List<Category> = emptyList(),
    val origin: String = "",
)

/** **Unread** (BR-06): followed topics first, then the others; it updates by itself. */
@HiltViewModel
class UnreadViewModel
    @Inject
    constructor(
        sessions: SessionManager,
    ) : ViewModel() {
        private val session = sessions.active
        private val feed = session?.let { UnreadFeed(it, viewModelScope) }
        private val categories = Categories()

        val state: StateFlow<UnreadUi> =
            combine(feed?.state ?: flowOf(UnreadState()), categories.all) { unread, all ->
                UnreadUi(unread, all, session?.origin.orEmpty())
            }.stateIn(viewModelScope, SharingStarted.Eagerly, UnreadUi())

        init {
            feed?.start()
            viewModelScope.launch { categories.load(session) }
        }

        fun refresh() {
            feed?.refresh()
            viewModelScope.launch { categories.load(session) }
        }

        fun more() {
            feed?.more()
        }
    }
