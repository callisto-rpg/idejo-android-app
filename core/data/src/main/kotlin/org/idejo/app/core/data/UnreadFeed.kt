// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.idejo.app.core.api.map
import org.idejo.app.core.model.Topic
import org.idejo.app.core.realtime.IdejoEvent

/** The Unread page (BR-06): every followed topic with news, then the others a page at a time. */
data class UnreadState(
    val following: List<Topic> = emptyList(),
    val others: PagesState<Topic> = PagesState(),
) {
    /** Nothing new since the last visit: both lists read and empty. */
    val empty: Boolean
        get() =
            !others.loading && others.problem == null && following.isEmpty() &&
                others.items.isEmpty()
}

/**
 * Reads `GET /unread` for [session]'s forum: the followed topics come with its first page.
 * It reads again by itself when `unread.changed` comes (BR-06), once a burst of them ends.
 */
class UnreadFeed(
    private val session: ForumSession,
    private val scope: CoroutineScope,
) {
    private val following = MutableStateFlow<List<Topic>>(emptyList())
    private val others =
        Pages<Topic>(scope, key = { it.id }) { cursor ->
            val key = cursor?.let { "unread:$it" } ?: "unread"
            session.cached(key) { unread(cursor) }.map { unread ->
                // Conversations never show in Unread (PC-04), whatever a server sends.
                if (cursor == null) following.value = unread.following.filterNot { it.private }
                unread.others.copy(data = unread.others.data.filterNot { it.private })
            }
        }
    private var listening: Job? = null

    // The followed topics come with the first page and are set just before it, so each page
    // state reads them as they are: never the new page with the old followed topics, which
    // combining the two flows could show for a moment (an empty page flashing).
    val state: StateFlow<UnreadState> =
        others.state
            .map { UnreadState(following.value, it) }
            .stateIn(scope, SharingStarted.Eagerly, UnreadState())

    /** Reads the first page, and from now on again on each change. */
    fun start() {
        others.refresh()
        if (listening != null) return
        listening =
            scope.launch {
                session.userEvents.filterIsInstance<IdejoEvent.UnreadChanged>().collectLatest {
                    delay(SETTLE_MS)
                    others.refresh()
                }
            }
    }

    fun refresh() = others.refresh()

    fun more() = others.more()

    private companion object {
        /** Messages come in bursts: one read once they settle. */
        const val SETTLE_MS = 500L
    }
}
