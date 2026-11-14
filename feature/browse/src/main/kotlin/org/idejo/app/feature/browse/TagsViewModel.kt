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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.data.Pages
import org.idejo.app.core.data.PagesState
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.model.Category
import org.idejo.app.core.model.Page
import org.idejo.app.core.model.Tag
import org.idejo.app.core.model.Topic
import javax.inject.Inject

/** The choice made: tags ticked and the operator. */
data class TagChoice(
    val ticked: Set<String> = emptySet(),
    val any: Boolean = false,
)

/** **Filter by tag** (BR-04). */
data class TagsUi(
    val tags: List<Tag> = emptyList(),
    val tagsLoading: Boolean = true,
    val tagsProblem: ApiProblem? = null,
    val choice: TagChoice = TagChoice(),
    /** What **Show topics** last listed; null before. */
    val shown: TagChoice? = null,
    val topics: PagesState<Topic> = PagesState(),
    val categories: List<Category> = emptyList(),
    val origin: String = "",
)

/** **Filter by tag** (BR-04): the forum's tags to tick, And or Or, and the topics found. */
@HiltViewModel
class TagsViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        sessions: SessionManager,
    ) : ViewModel() {
        private val session = sessions.active
        private val categories = Categories()
        private val form = MutableStateFlow(Form())

        private val pages =
            Pages<Topic>(viewModelScope, key = { it.id }) { cursor ->
                val shown = form.value.shown
                if (session == null || shown == null || shown.ticked.isEmpty()) {
                    ApiResult.Success(Page())
                } else {
                    session.call { tagged(shown.ticked.toList(), shown.any, cursor) }
                }
            }

        val state: StateFlow<TagsUi> =
            combine(form, pages.state, categories.all) { form, topics, categories ->
                TagsUi(
                    tags = form.tags,
                    tagsLoading = form.loading,
                    tagsProblem = form.problem,
                    choice = form.choice,
                    shown = form.shown,
                    topics = topics,
                    categories = categories,
                    origin = session?.origin.orEmpty(),
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, TagsUi())

        init {
            val ticked =
                savedState
                    .get<String>("tags")
                    .orEmpty()
                    .split(',')
                    .filter { it.isNotBlank() }
                    .toSet()
            form.update { it.copy(choice = TagChoice(ticked)) }
            loadTags()
            viewModelScope.launch { categories.load(session) }
            if (ticked.isNotEmpty()) show()
        }

        fun loadTags() {
            form.update { it.copy(loading = true, problem = null) }
            viewModelScope.launch {
                when (val result = session?.call { tags() } ?: return@launch) {
                    is ApiResult.Success -> {
                        form.update { it.copy(tags = result.value, loading = false) }
                    }

                    is ApiResult.Failure -> {
                        form.update { it.copy(loading = false, problem = result.problem) }
                    }
                }
            }
        }

        fun tick(slug: String) =
            form.update {
                val ticked = it.choice.ticked
                it.copy(
                    choice =
                        it.choice.copy(
                            ticked =
                                if (slug in
                                    ticked
                                ) {
                                    ticked - slug
                                } else {
                                    ticked + slug
                                },
                        ),
                )
            }

        /** **Operator**: as on the web, choosing it lists the topics again at once. */
        fun operator(any: Boolean) {
            form.update { it.copy(choice = it.choice.copy(any = any)) }
            if (form.value.choice.ticked
                    .isNotEmpty()
            ) {
                show()
            }
        }

        /** **Show topics**. */
        fun show() {
            form.update { it.copy(shown = it.choice) }
            pages.refresh()
        }

        fun refresh() {
            loadTags()
            if (form.value.shown != null) pages.refresh()
        }

        fun more() = pages.more()

        private data class Form(
            val tags: List<Tag> = emptyList(),
            val loading: Boolean = true,
            val problem: ApiProblem? = null,
            val choice: TagChoice = TagChoice(),
            val shown: TagChoice? = null,
        )
    }
