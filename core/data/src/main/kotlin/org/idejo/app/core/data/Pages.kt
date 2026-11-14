// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.model.Page

/** A list read a page at a time, as [Pages] shows it. */
data class PagesState<T>(
    val items: List<T> = emptyList(),
    /** The first page is being read, nothing shown yet. */
    val loading: Boolean = true,
    /** Pull to refresh (BR-10): what was shown stays until the new first page comes. */
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    /** No page after the last one read. */
    val end: Boolean = false,
    /** Why the last read failed; what was shown stays. */
    val problem: ApiProblem? = null,
) {
    /** A page is being read. */
    val busy: Boolean get() = loading || refreshing || loadingMore
}

/**
 * A cursor-paged list (BR-10, API guide §4): the first page, the next one when the reader
 * nears the end ([more]), all again on [refresh]. [read] asks for the page after a cursor
 * (null: the first). One read at a time; a refresh drops a read of more under way.
 */
class Pages<T>(
    private val scope: CoroutineScope,
    private val key: (T) -> Any,
    private val read: suspend (cursor: String?) -> ApiResult<Page<T>>,
) {
    private val mutableState = MutableStateFlow(PagesState<T>())
    val state: StateFlow<PagesState<T>> = mutableState.asStateFlow()

    private var next: String? = null
    private var job: Job? = null

    /** Reads the first page; shown items stay while it comes. */
    fun refresh() {
        job?.cancel()
        next = null
        mutableState.update {
            it.copy(refreshing = it.items.isNotEmpty(), loading = it.items.isEmpty())
        }
        job =
            scope.launch {
                when (val result = read(null)) {
                    is ApiResult.Success -> {
                        next = result.value.nextCursor
                        mutableState.value =
                            PagesState(
                                items = result.value.data.distinctBy(key),
                                loading = false,
                                end = next == null,
                            )
                    }

                    is ApiResult.Failure -> {
                        failed(result.problem)
                    }
                }
            }
    }

    /** Reads the next page, unless one is being read or the list has ended. */
    fun more() {
        // No cursor: the first page is still coming, or the list has ended. The state, not
        // the job, says whether a read is under way: the job ends just after the state.
        val cursor = next
        if (cursor == null || state.value.busy) return
        mutableState.update { it.copy(loadingMore = true, problem = null) }
        job =
            scope.launch {
                when (val result = read(cursor)) {
                    is ApiResult.Success -> {
                        next = result.value.nextCursor
                        mutableState.update {
                            it.copy(
                                items = (it.items + result.value.data).distinctBy(key),
                                loadingMore = false,
                                end = next == null,
                            )
                        }
                    }

                    is ApiResult.Failure -> {
                        failed(result.problem)
                    }
                }
            }
    }

    /** One item as the server now has it (a follow, a mute), kept in place. */
    fun replace(item: T) {
        mutableState.update { state ->
            state.copy(items = state.items.map { if (key(it) == key(item)) item else it })
        }
    }

    private fun failed(problem: ApiProblem) =
        mutableState.update {
            it.copy(loading = false, refreshing = false, loadingMore = false, problem = problem)
        }
}
