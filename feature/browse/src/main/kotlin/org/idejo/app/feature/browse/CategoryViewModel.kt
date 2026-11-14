// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.getOrNull
import org.idejo.app.core.data.Pages
import org.idejo.app.core.data.PagesState
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.data.TopicView
import org.idejo.app.core.model.Category
import org.idejo.app.core.model.Page
import org.idejo.app.core.model.Topic
import javax.inject.Inject

/** What a category page shows. */
data class CategoryUi(
    val category: Category? = null,
    val topics: PagesState<Topic> = PagesState(),
    val view: TopicView = TopicView.Compact,
    val origin: String = "",
)

/** A category's page (BR-02, BR-03, BR-10): its topics a page at a time, pinned first. */
@HiltViewModel
class CategoryViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val sessions: SessionManager,
    ) : ViewModel() {
        private val id: String = checkNotNull(savedState["id"])
        private val session = sessions.active
        private val category = MutableStateFlow<Category?>(null)
        private var expanded = session?.record?.value?.topicView == TopicView.Expanded

        private val pages =
            Pages<Topic>(viewModelScope, key = { it.id }) { cursor ->
                session?.call { categoryTopics(id, cursor, expanded) }
                    ?: ApiResult.Success(Page())
            }

        val state: StateFlow<CategoryUi> =
            combine(
                category,
                pages.state,
                session?.record?.map { it.topicView } ?: flowOf(TopicView.Compact),
            ) { category, topics, view ->
                CategoryUi(category, topics, view, session?.origin.orEmpty())
            }.stateIn(viewModelScope, SharingStarted.Eagerly, CategoryUi())

        init {
            refresh()
        }

        fun refresh() {
            pages.refresh()
            viewModelScope.launch {
                session?.call { category(id) }?.getOrNull()?.let { category.value = it }
            }
        }

        fun more() = pages.more()

        /** **View** (BR-03): remembered per forum; Expanded reads the topics again. */
        fun view(view: TopicView) {
            viewModelScope.launch { sessions.updateForum { it.copy(topicView = view) } }
            if (view == TopicView.Expanded && !expanded) {
                expanded = true
                pages.refresh()
            }
        }
    }
