// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.idejo.app.core.data.HomeFeed
import org.idejo.app.core.data.HomeState
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.data.TopicView
import javax.inject.Inject

/** What the home page shows, and how its topics look. */
data class HomeUi(
    val home: HomeState = HomeState(),
    val view: TopicView = TopicView.Compact,
    val origin: String = "",
)

/** The home page (BR-01, BR-03, BR-10) of the forum open. */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val sessions: SessionManager,
    ) : ViewModel() {
        private val session = sessions.active
        private val feed = session?.let { HomeFeed(it, viewModelScope) }

        val state: StateFlow<HomeUi> =
            if (session == null || feed == null) {
                emptyFlow()
            } else {
                combine(feed.state, session.record.map { it.topicView }) { home, view ->
                    HomeUi(home, view, session.origin)
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUi())

        init {
            val view = session?.record?.value?.topicView ?: TopicView.Compact
            feed?.refresh(expanded = view == TopicView.Expanded)
        }

        fun refresh() {
            feed?.refresh(expanded = state.value.view == TopicView.Expanded)
        }

        /** **View** (BR-03): remembered per forum; Expanded reads the previews. */
        fun view(view: TopicView) {
            viewModelScope.launch { sessions.updateForum { it.copy(topicView = view) } }
            if (view == TopicView.Expanded && feed?.state?.value?.expanded != true) {
                feed?.refresh(expanded = true)
            }
        }
    }
