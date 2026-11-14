// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.model.Category
import org.idejo.app.core.model.Topic

/** The home page (BR-01): the categories in their groups, then the active topics. */
data class HomeState(
    val sections: List<SidebarSection> = emptyList(),
    val active: List<Topic> = emptyList(),
    /** Whether [active] was read with previews (BR-03). */
    val expanded: Boolean = false,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val problem: ApiProblem? = null,
) {
    fun category(id: String?): Category? =
        sections.firstNotNullOfOrNull { s ->
            s.categories.firstOrNull { it.category.id == id }?.category
        }
}

/** Reads the home page of [session]'s forum: `GET /categories` and `GET /topics/active`. */
class HomeFeed(
    private val session: ForumSession,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = mutableState.asStateFlow()
    private var job: Job? = null

    /** Reads both again; [expanded] asks for the active topics' previews. */
    fun refresh(expanded: Boolean) {
        job?.cancel()
        mutableState.update {
            it.copy(refreshing = it.sections.isNotEmpty(), loading = it.sections.isEmpty())
        }
        job = scope.launch { load(expanded) }
    }

    private suspend fun load(expanded: Boolean) =
        coroutineScope {
            val categories = async { session.call { categories() } }
            val active = async { session.call { activeTopics(expanded) } }
            val read = categories.await()
            val topics = active.await()
            when {
                read is ApiResult.Failure -> {
                    failed(read.problem)
                }

                topics is ApiResult.Failure -> {
                    failed(topics.problem)
                }

                else -> {
                    mutableState.value =
                        HomeState(
                            sections = sections((read as ApiResult.Success).value, emptyList()),
                            active = (topics as ApiResult.Success).value,
                            expanded = expanded,
                            loading = false,
                        )
                }
            }
        }

    private fun failed(problem: ApiProblem) =
        mutableState.update { it.copy(loading = false, refreshing = false, problem = problem) }
}
