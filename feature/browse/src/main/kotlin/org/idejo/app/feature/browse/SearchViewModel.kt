// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.data.Pages
import org.idejo.app.core.data.PagesState
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.model.Category
import org.idejo.app.core.model.Page
import org.idejo.app.core.model.SearchHit
import javax.inject.Inject

/** What was searched for: the words and, perhaps, one category. */
data class SearchQuery(
    val words: String = "",
    val categoryId: String? = null,
)

/** **Search** (BR-05). */
data class SearchUi(
    val query: SearchQuery = SearchQuery(),
    /** The query whose results are shown; null before the first search. */
    val searched: SearchQuery? = null,
    val hits: PagesState<SearchHit> = PagesState(loading = false),
    val categories: List<Category> = emptyList(),
)

/** **Search** (BR-05): words and a category, the messages found a page at a time. */
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        sessions: SessionManager,
    ) : ViewModel() {
        private val session = sessions.active
        private val categories = Categories()
        private val query = MutableStateFlow(SearchQuery())
        private val searched = MutableStateFlow<SearchQuery?>(null)

        private val pages =
            Pages<SearchHit>(viewModelScope, key = { it.messageId }) { cursor ->
                val asked = searched.value
                if (session == null || asked == null) {
                    ApiResult.Success(Page())
                } else {
                    session.call { search(asked.words, asked.categoryId, cursor) }
                }
            }

        val state: StateFlow<SearchUi> =
            combine(query, searched, pages.state, categories.all) { query, searched, hits, all ->
                SearchUi(
                    query,
                    searched,
                    if (searched ==
                        null
                    ) {
                        PagesState(loading = false)
                    } else {
                        hits
                    },
                    all,
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchUi())

        init {
            viewModelScope.launch { categories.load(session) }
        }

        fun words(words: String) = query.update { it.copy(words = words) }

        fun category(id: String?) = query.update { it.copy(categoryId = id) }

        /** **Search**: nothing for blank words, as on the web. */
        fun search() {
            val asked = query.value.copy(words = query.value.words.trim())
            if (asked.words.isEmpty()) return
            searched.value = asked
            pages.refresh()
        }

        fun refresh() {
            if (searched.value != null) pages.refresh()
        }

        fun more() = pages.more()
    }
