// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.model.Category
import org.idejo.app.core.model.CategoryGroup
import org.idejo.app.core.model.Topic
import org.idejo.app.core.model.UnreadCounter
import org.idejo.app.core.realtime.EventStream
import org.idejo.app.core.realtime.IdejoEvent

/** A category in the sidebar, with the topics the member follows there (SH-31). */
data class SidebarCategory(
    val category: Category,
    val topics: List<Topic>,
)

/** Categories under their group, as on the home page; [group] null for those without one. */
data class SidebarSection(
    val group: CategoryGroup?,
    val categories: List<SidebarCategory>,
)

/** What the sidebar and the top bar's **Unread** show. */
data class SidebarState(
    val sections: List<SidebarSection> = emptyList(),
    val unread: UnreadCounter = UnreadCounter(),
    val loading: Boolean = true,
    /** Why the last load failed, when it did; what was loaded before stays. */
    val problem: ApiProblem? = null,
    /** Topics whose name glows (SH-32): a number that changes with each new message. */
    val glowing: Map<String, Int> = emptyMap(),
    /** The forum does not answer: follows and Mark unread wait (OF-05). */
    val offline: Boolean = false,
) {
    val topics: List<Topic> get() = sections.flatMap { s -> s.categories.flatMap { it.topics } }

    fun category(id: String?): Category? =
        sections.firstNotNullOfOrNull { s ->
            s.categories.firstOrNull { it.category.id == id }?.category
        }
}

/**
 * The left sidebar's categories and followed topics, and the **Unread** counter, for one
 * forum, kept up to date from the category channels and `user:me` (SH-31, SH-32, SH-34).
 *
 * The followed topics come from `GET /me/followed` (IX-02), in the order followed, up to
 * [FOLLOWED_PAGES] pages. A server without it (before idejo 0.3.16) gets the spec's
 * fallback: each category's topics filtered by `followed`, the most recently active [PAGES]
 * pages of each, in the category's order (latest activity first). Either way with every
 * followed topic `GET /unread` lists.
 */
@Suppress("TooManyFunctions") // One per thing the sidebar does or hears.
class ForumSidebar(
    private val session: ForumSession,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(SidebarState())
    val state: StateFlow<SidebarState> = mutableState.asStateFlow()

    private val channels = mutableMapOf<String, EventStream>()
    private var listening: Job? = null
    private var pendingUnread: Job? = null
    private var pendingReload: Job? = null
    private var glows = 0

    private val signedIn: Boolean get() = session.access.value == Access.SignedIn

    /** Loads the sidebar and listens for changes; [close] stops. */
    fun start() {
        refresh()
        if (listening != null) return
        listening =
            scope.launch {
                session.userEvents.collect { event ->
                    if (event is IdejoEvent.UnreadChanged) unreadChanged(event)
                }
            }
    }

    /** Reads everything again (pull to refresh, sign-in, `topic.moved`). */
    fun refresh() {
        scope.launch { load() }
    }

    fun close() {
        listening?.cancel()
        listening = null
        channels.values.forEach { it.leave() }
        channels.clear()
    }

    /** **+** and **×** (SH-34), and Follow/Unfollow in a topic's menu (SH-37). */
    fun follow(
        topic: Topic,
        followed: Boolean,
    ) = scope.launch {
        session.call { follow(topic.id, followed) }.ifSuccess { changed(it) }
    }

    /** **Unfollow & mute** (SH-37): the topic tells nothing any more. */
    fun unfollowAndMute(topic: Topic) =
        scope.launch {
            session.call { mute(topic.id, true) }.ifSuccess { changed(it) }
        }

    /** **Mark unread** (SH-37): its latest message by someone else is new again. */
    fun markUnread(topic: Topic) =
        scope.launch {
            session.call { markUnread(topic.id) }.ifSuccess { changed(it) }
        }

    private suspend fun load() {
        mutableState.update { it.copy(loading = true) }
        val categories =
            when (val result = session.cached("categories") { categories() }) {
                is ApiResult.Failure -> return failed(result.problem)
                is ApiResult.Success -> result.value
            }
        val unread = if (signedIn) session.cached("unread") { unread() } else null
        if (unread is ApiResult.Failure) return failed(unread.problem)
        val withNews = (unread as? ApiResult.Success)?.value
        val followed =
            if (signedIn) {
                when (val result = followedList() ?: followedIn(categories)) {
                    is ApiResult.Failure -> return failed(result.problem)
                    is ApiResult.Success -> result.value
                }
            } else {
                emptyList()
            }
        // Conversations are never among the categories (PC-04): they have none.
        val topics =
            (followed + withNews?.following.orEmpty())
                .filterNot { it.private }
                .distinctBy { it.id }
        mutableState.update {
            it.copy(
                sections = sections(categories, topics),
                unread = withNews?.counter ?: UnreadCounter(),
                loading = false,
                problem = null,
            )
        }
        listen(categories)
    }

    private fun failed(problem: ApiProblem) =
        mutableState.update { it.copy(loading = false, problem = problem) }

    /** `GET /me/followed` (IX-02), page after page; null on a server without it (404). */
    private suspend fun followedList(): ApiResult<List<Topic>>? {
        val found = mutableListOf<Topic>()
        var cursor: String? = null
        repeat(FOLLOWED_PAGES) {
            val page =
                when (val result = session.cached("followed:$cursor") { followed(cursor, SIZE) }) {
                    is ApiResult.Failure -> {
                        val missing = (result.problem as? ApiProblem.Server)?.status == NOT_FOUND
                        return if (missing) null else result
                    }

                    is ApiResult.Success -> {
                        result.value
                    }
                }
            found += page.data.filterNot { it.muted }
            cursor = page.nextCursor ?: return ApiResult.Success(found)
        }
        return ApiResult.Success(found)
    }

    private suspend fun followedIn(categories: List<Category>): ApiResult<List<Topic>> =
        coroutineScope {
            val pages = categories.map { async { followedIn(it) } }.awaitAll()
            pages.firstOrNull { it is ApiResult.Failure }
                ?: ApiResult.Success(pages.flatMap { (it as ApiResult.Success).value })
        }

    private suspend fun followedIn(category: Category): ApiResult<List<Topic>> {
        val found = mutableListOf<Topic>()
        var cursor: String? = null
        repeat(PAGES) {
            val page =
                when (
                    val result =
                        session.call {
                            categoryTopics(
                                category.id,
                                cursor,
                                limit = SIZE,
                            )
                        }
                ) {
                    is ApiResult.Failure -> return result
                    is ApiResult.Success -> result.value
                }
            found += page.data.filter { it.followed && !it.muted }
            cursor = page.nextCursor ?: return ApiResult.Success(found)
        }
        return ApiResult.Success(found)
    }

    /** Joins the channels of the categories shown, and leaves those gone. */
    private fun listen(categories: List<Category>) {
        if (!signedIn) return
        val ids = categories.map { it.id }.toSet()
        (channels.keys - ids).forEach { channels.remove(it)?.leave() }
        (ids - channels.keys).forEach { id ->
            val stream = session.events.category(id)
            channels[id] = stream
            scope.launch { stream.events.collect(::categoryEvent) }
            stream.join()
        }
    }

    private fun categoryEvent(event: IdejoEvent) {
        when (event) {
            is IdejoEvent.TopicActivity -> {
                if (state.value.topics.any { it.id == event.topicId }) {
                    mutableState.update {
                        it.copy(glowing = it.glowing + (event.topicId to ++glows))
                    }
                    readUnreadSoon()
                }
            }

            is IdejoEvent.TopicCreated -> {
                if (event.topic.followed) changed(event.topic)
            }

            is IdejoEvent.TopicMovedOut, is IdejoEvent.TopicArchived -> {
                reloadSoon()
            }

            else -> {
                Unit
            }
        }
    }

    private fun unreadChanged(event: IdejoEvent.UnreadChanged) {
        mutableState.update { it.copy(unread = UnreadCounter(event.count, event.level)) }
        readUnreadSoon()
    }

    /** The dots again from `GET /unread`, once a burst of events is over. */
    private fun readUnreadSoon() {
        pendingUnread?.cancel()
        pendingUnread =
            scope.launch {
                delay(SETTLE_MILLIS)
                val unread = session.call { unread() }
                if (unread is ApiResult.Success) dots(unread.value.following, unread.value.counter)
            }
    }

    private fun reloadSoon() {
        pendingReload?.cancel()
        pendingReload =
            scope.launch {
                delay(SETTLE_MILLIS)
                load()
            }
    }

    /** The followed topics' unread counts and dots from [following], the topics with news. */
    private fun dots(
        following: List<Topic>,
        counter: UnreadCounter,
    ) {
        val news = following.filterNot { it.private }.associateBy { it.id }
        mutableState.update { state ->
            val known = state.topics.map { it.id }.toSet()
            val sections =
                state.sections.mapTopics { topics ->
                    topics.map { topic ->
                        news[topic.id]
                            ?: topic.copy(
                                unreadCount = 0,
                                mentionState = org.idejo.app.core.model.MentionState.None,
                            )
                    }
                }
            // A followed topic with news the pages did not reach.
            val added = news.values.filter { it.id !in known }
            state.copy(
                sections = if (added.isEmpty()) sections else sections.with(added),
                unread = counter,
            )
        }
    }

    /** A topic as the server now has it: in the sidebar while followed, else out. */
    private fun changed(topic: Topic) {
        mutableState.update { state ->
            val without =
                state.sections.mapTopics { topics ->
                    topics.filterNot { it.id == topic.id }
                }
            val replaced =
                state.sections.mapTopics { topics ->
                    topics.map { if (it.id == topic.id) topic else it }
                }
            val present = state.topics.any { it.id == topic.id }
            state.copy(
                sections =
                    when {
                        !topic.followed || topic.muted -> without
                        present -> replaced
                        else -> state.sections.with(listOf(topic))
                    },
            )
        }
    }

    private companion object {
        /** Pages of each category read for followed topics (IX-02 fallback). */
        const val PAGES = 3

        /** Pages of `GET /me/followed` read (IX-02): a thousand topics. */
        const val FOLLOWED_PAGES = 10
        private const val NOT_FOUND = 404
        const val SIZE = 100
        const val SETTLE_MILLIS = 300L
    }
}

private inline fun <T> ApiResult<T>.ifSuccess(block: (T) -> Unit) {
    if (this is ApiResult.Success) block(value)
}

private fun List<SidebarSection>.mapTopics(
    transform: (List<Topic>) -> List<Topic>,
): List<SidebarSection> =
    map { section ->
        section.copy(categories = section.categories.map { it.copy(topics = transform(it.topics)) })
    }

/** [added] topics put at the end of their categories. */
private fun List<SidebarSection>.with(added: List<Topic>): List<SidebarSection> =
    map { section ->
        section.copy(
            categories =
                section.categories.map { entry ->
                    val mine = added.filter { it.categoryId == entry.category.id }
                    if (mine.isEmpty()) entry else entry.copy(topics = entry.topics + mine)
                },
        )
    }

/**
 * The home page's order (idejo's sections): each group in its order with its categories,
 * then the categories without a group; categories by position within each.
 */
fun sections(
    categories: List<Category>,
    topics: List<Topic>,
): List<SidebarSection> {
    val byCategory = topics.groupBy { it.categoryId }
    val entries =
        categories
            .sortedBy { it.position }
            .map { SidebarCategory(it, byCategory[it.id].orEmpty()) }
    val byGroup = entries.groupBy { it.category.group?.id }
    val groups =
        categories
            .mapNotNull { it.group }
            .distinctBy { it.id }
            .sortedBy { it.position }
    return groups.map { SidebarSection(it, byGroup[it.id].orEmpty()) } +
        listOfNotNull(byGroup[null]?.let { SidebarSection(null, it) })
}
