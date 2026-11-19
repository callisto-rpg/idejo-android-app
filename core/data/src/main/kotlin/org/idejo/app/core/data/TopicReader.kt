// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.model.ApiError
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.MessageWindow
import org.idejo.app.core.model.Topic
import org.idejo.app.core.model.UnreadMark
import org.idejo.app.core.realtime.IdejoEvent
import org.idejo.app.core.realtime.TopicStream

/** A topic page's messages (TV-01 to TV-06). */
data class TopicState(
    val topic: Topic? = null,
    /** The opening message, on top, when [messages] does not start with it (TV-01). */
    val opening: Message? = null,
    /** The window, in order. */
    val messages: List<Message> = emptyList(),
    /** Messages before and after the window, the opening one left out (IX-09). */
    val earlier: Int = 0,
    val later: Int = 0,
    /** Where the member left off: the red **N unread messages** line (TV-01). */
    val unread: UnreadMark? = null,
    /**
     * The first unread message when the page opened, kept after the line fades: mentions
     * from there on glow as they are reached (TV-07).
     */
    val glowFrom: String? = null,
    /** New messages that came while the window does not reach the end (TV-05). */
    val newBelow: Int = 0,
    /**
     * The ids counted in [newBelow]: a message that comes both live and in a catch-up (it
     * was posted while the topic was being joined) is counted once.
     */
    val countedBelow: Set<String> = emptySet(),
    /** The message the window was loaded around, to scroll to. */
    val landing: String? = null,
    val loading: Boolean = true,
    val loadingEarlier: Boolean = false,
    val loadingLater: Boolean = false,
    /** Why the last read failed; what was shown stays. */
    val problem: ApiProblem? = null,
    /** The member may no longer view the topic (`forbidden` on its channel). */
    val gone: Boolean = false,
    /** The polls whose vote is being sent, by poll id (TV-27). */
    val voting: Set<String> = emptySet(),
) {
    /** The window reaches the topic's last message. */
    val atEnd: Boolean get() = later == 0
}

/**
 * Reads topic [topicId] of [session]'s forum: the window (landing on [landing], else the first
 * unread, else the end), more of it both ways, and its channel while [start]ed, catching up
 * after a reconnect (TV-03, TV-04, TV-12). [seen] moves the read position (TV-06).
 */
@Suppress("TooManyFunctions") // One per thing the page does or hears.
class TopicReader(
    private val session: ForumSession,
    private val topicId: String,
    private val scope: CoroutineScope,
    private val landing: String? = null,
    /** A private conversation (PC-01): its channel is `conversation:<id>`. */
    private val private: Boolean = false,
) {
    private val mutableState = MutableStateFlow(TopicState())
    val state: StateFlow<TopicState> = mutableState.asStateFlow()

    // Loads and live changes take turns, so a change never lands on a window being replaced.
    private val turns = Mutex()
    private var before: String? = null
    private var after: String? = null
    private var stream: TopicStream? = null
    private var listening: Job? = null
    private var reading: Job? = null
    private var readUpTo: Int = -1

    /** Loads the window and joins the topic's channel; [stop] leaves it. */
    fun start() {
        load(landing)
        if (stream != null) return
        val topic =
            if (private) session.events.conversation(topicId) else session.events.topic(topicId)
        stream = topic
        listening = scope.launch { topic.events.collect { turns.withLock { live(it, topic) } } }
        topic.join()
    }

    /** Leaves the channel: the member gets pushes for the topic again (TV-12). */
    fun stop() {
        listening?.cancel()
        listening = null
        stream?.leave()
        stream = null
    }

    /** Loads the window around [messageId], or where the member left off. */
    fun load(messageId: String? = landing) {
        mutableState.update { it.copy(loading = true, problem = null) }
        scope.launch { turns.withLock { loadNow(messageId) } }
    }

    /** The window at the topic's end: **Jump to the end to write**, **N new messages**. */
    fun end() {
        mutableState.update { it.copy(loading = true, problem = null) }
        scope.launch { turns.withLock { loadNow(null, end = true) } }
    }

    /** The messages before the window (TV-05). */
    fun earlier() {
        val cursor = before ?: return
        if (state.value.loadingEarlier) return
        mutableState.update { it.copy(loadingEarlier = true, problem = null) }
        scope.launch {
            turns.withLock {
                when (val result = session.call { messages(topicId, cursor = cursor) }) {
                    is ApiResult.Success -> {
                        val window = result.value
                        before = window.prevCursor
                        mutableState.update { state ->
                            val known = state.messages.map { it.id }.toSet()
                            val more = window.data.filter { it.id !in known }
                            state.copy(
                                messages = more + state.messages,
                                earlier =
                                    window.earlier ?: (state.earlier - more.size).coerceAtLeast(0),
                                opening = openingOf(window, more + state.messages),
                                loadingEarlier = false,
                            )
                        }
                    }

                    is ApiResult.Failure -> {
                        mutableState.update {
                            it.copy(loadingEarlier = false, problem = result.problem)
                        }
                    }
                }
            }
        }
    }

    /** The messages after the window (TV-05). */
    fun later() {
        val cursor = after ?: return
        if (state.value.loadingLater) return
        mutableState.update { it.copy(loadingLater = true, problem = null) }
        scope.launch {
            turns.withLock {
                when (val result = session.call { messages(topicId, cursor = cursor) }) {
                    is ApiResult.Success -> {
                        val window = result.value
                        after = window.nextCursor
                        mutableState.update { state ->
                            val known = state.messages.map { it.id }.toSet()
                            val more = window.data.filter { it.id !in known }
                            val later = window.later ?: (state.later - more.size).coerceAtLeast(0)
                            state.copy(
                                messages = state.messages + more,
                                later = if (after == null) 0 else later,
                                newBelow = if (after == null) 0 else state.newBelow,
                                countedBelow =
                                    if (after ==
                                        null
                                    ) {
                                        emptySet()
                                    } else {
                                        state.countedBelow
                                    },
                                loadingLater = false,
                            )
                        }
                    }

                    is ApiResult.Failure -> {
                        mutableState.update {
                            it.copy(loadingLater = false, problem = result.problem)
                        }
                    }
                }
            }
        }
    }

    /**
     * The member saw [messageId] on screen: the read position moves there, once the scrolling
     * settles, and never back (TV-06).
     */
    fun seen(messageId: String) {
        val index = state.value.messages.indexOfFirst { it.id == messageId }
        if (index <= readUpTo) return
        readUpTo = index
        reading?.cancel()
        reading =
            scope.launch {
                delay(READ_SETTLE_MS)
                session.call { read(topicId, messageId) }.let { result ->
                    if (result is ApiResult.Success) viewed(result.value)
                }
            }
    }

    /** The unread line has faded (TV-01). */
    fun unreadShown() = mutableState.update { it.copy(unread = null) }

    /** **Follow** / **Unfollow** (TV-10). */
    fun follow(followed: Boolean) {
        scope.launch {
            when (val result = session.call { follow(topicId, followed) }) {
                is ApiResult.Success -> viewed(result.value)
                is ApiResult.Failure -> mutableState.update { it.copy(problem = result.problem) }
            }
        }
    }

    /**
     * The member's answers to the poll under [messageId] (TV-27): sent, then the poll as the
     * server answers it. A refusal shows its message; the poll stays as it was.
     */
    fun vote(
        messageId: String,
        pollId: String,
        optionIds: List<String>,
    ) {
        mutableState.update { it.copy(voting = it.voting + pollId) }
        scope.launch {
            val result = session.call { vote(pollId, optionIds) }
            if (result is ApiResult.Success) edit(messageId) { it.copy(poll = result.value) }
            mutableState.update {
                it.copy(
                    voting = it.voting - pollId,
                    problem = (result as? ApiResult.Failure)?.problem ?: it.problem,
                )
            }
        }
    }

    /** A reaction added or taken back (MA-03); the server's answer replaces the message's. */
    fun react(
        messageId: String,
        name: String,
        on: Boolean,
    ) {
        scope.launch {
            when (val result = session.call { react(messageId, name, on) }) {
                is ApiResult.Success -> edit(messageId) { it.copy(reactions = result.value) }
                is ApiResult.Failure -> mutableState.update { it.copy(problem = result.problem) }
            }
        }
    }

    /**
     * Where message [messageId] is, for a quote or a banner leading to it: in this topic
     * (the window loads around it unless it is shown), or in another topic, whose id comes
     * back for the app to open. Null when the member may not read it.
     */
    suspend fun reach(messageId: String): String? {
        val shown =
            state.value.messages.any { it.id == messageId } || state.value.opening?.id == messageId
        if (shown) return topicId
        val message = session.call { message(messageId) } as? ApiResult.Success ?: return null
        val home = message.value.topicId
        if (home == topicId) load(messageId)
        return home
    }

    private suspend fun loadNow(
        messageId: String?,
        end: Boolean = false,
    ) {
        val topic = session.cached(PageCache.TOPIC + topicId) { topic(topicId) }.named()
        val window =
            session.cached(PageCache.WINDOW + topicId) {
                when {
                    end -> messages(topicId)
                    messageId != null -> messages(topicId, around = messageId)
                    else -> messages(topicId, firstUnread = true)
                }
            }
        if (topic is ApiResult.Failure) return failed(topic.problem)
        if (window is ApiResult.Failure) return failed(window.problem)
        val loaded = (window as ApiResult.Success).value
        before = loaded.prevCursor
        after = loaded.nextCursor
        readUpTo = -1
        stream?.caughtUp(loaded.cursor)
        val unread = loaded.firstUnread?.takeIf { !end && messageId == null }
        mutableState.value =
            TopicState(
                topic = (topic as ApiResult.Success).value,
                opening = openingOf(loaded, loaded.data),
                messages = loaded.data,
                earlier = loaded.earlier ?: if (before == null) 0 else loaded.total,
                later = loaded.later ?: if (after == null) 0 else loaded.total,
                unread = unread,
                glowFrom = unread?.messageId ?: mutableState.value.glowFrom,
                landing = messageId ?: unread?.messageId,
                loading = false,
            )
    }

    /** A conversation is named after the other participants, as on the web (PC-01). */
    private suspend fun ApiResult<Topic>.named(): ApiResult<Topic> {
        if (this !is ApiResult.Success || !value.private) return this
        val card = session.call { conversation(topicId) }
        val name = (card as? ApiResult.Success)?.value?.name?.takeIf { it.isNotBlank() }
        return if (name == null) this else ApiResult.Success(value.copy(title = name))
    }

    private fun failed(problem: ApiProblem) {
        val gone = (problem as? ApiProblem.Server)?.error?.code == ApiError.NOT_FOUND
        mutableState.update { it.copy(loading = false, problem = problem, gone = gone) }
    }

    private suspend fun live(
        event: IdejoEvent,
        topic: TopicStream,
    ) {
        when (event) {
            is IdejoEvent.MessageChanged -> {
                changed(event.message)
            }

            is IdejoEvent.ReactionsChanged -> {
                edit(event.messageId) { it.copy(reactions = event.reactions) }
            }

            is IdejoEvent.PollUpdated -> {
                edit(event.messageId) { it.copy(poll = event.poll) }
            }

            is IdejoEvent.TopicChanged -> {
                keepViewerFields(event.topic)
            }

            is IdejoEvent.MessagesMoved -> {
                loadNow(
                    state.value.messages
                        .firstOrNull()
                        ?.id,
                )
            }

            is IdejoEvent.Forbidden -> {
                mutableState.update { it.copy(gone = true) }
                stop()
            }

            is IdejoEvent.CatchUp -> {
                catchUp(event.since, topic)
            }

            else -> {}
        }
    }

    /** `GET /changes?since=` until `more` is false; an expired cursor loads again (TV-04). */
    private suspend fun catchUp(
        since: Long?,
        topic: TopicStream,
    ) {
        var cursor =
            since ?: return loadNow(
                state.value.messages
                    .firstOrNull()
                    ?.id,
            )
        while (true) {
            when (val result = session.call { changes(topicId, cursor) }) {
                is ApiResult.Success -> {
                    result.value.data.forEach(::changed)
                    result.value.topic?.let(::keepViewerFields)
                    cursor = result.value.cursor
                    topic.caughtUp(cursor)
                    if (!result.value.more) return
                }

                is ApiResult.Failure -> {
                    val expired =
                        (result.problem as? ApiProblem.Server)?.error?.code ==
                            ApiError.CURSOR_EXPIRED
                    if (expired) {
                        loadNow(
                            state.value.messages
                                .firstOrNull()
                                ?.id,
                        )
                    }
                    return
                }
            }
        }
    }

    /** A message changed in place, or a new one: at the end, or counted below (TV-03, TV-05). */
    private fun changed(message: Message) {
        mutableState.update { state ->
            when {
                state.opening?.id == message.id -> {
                    state.copy(opening = message)
                }

                state.messages.any { it.id == message.id } -> {
                    state.copy(
                        messages =
                            state.messages.map {
                                if (it.id ==
                                    message.id
                                ) {
                                    message
                                } else {
                                    it
                                }
                            },
                    )
                }

                state.loading -> {
                    state
                }

                // Only a message the window does not hold yet; one before it is not shown.
                isNew(state, message) && state.atEnd && after == null -> {
                    state.copy(messages = state.messages + message)
                }

                message.id in state.countedBelow -> {
                    state
                }

                isNew(state, message) -> {
                    state.copy(
                        later = state.later + 1,
                        newBelow = state.newBelow + 1,
                        countedBelow = state.countedBelow + message.id,
                    )
                }

                else -> {
                    state
                }
            }
        }
    }

    private fun isNew(
        state: TopicState,
        message: Message,
    ): Boolean {
        val last = state.messages.lastOrNull() ?: return true
        val at = message.openedAt ?: return false
        return last.openedAt?.let { at >= it } ?: true
    }

    private fun edit(
        id: String,
        change: (Message) -> Message,
    ) = mutableState.update { state ->
        state.copy(
            opening = state.opening?.let { if (it.id == id) change(it) else it },
            messages = state.messages.map { if (it.id == id) change(it) else it },
        )
    }

    /** The topic as the server answered the member: their own follow and read state. */
    private fun viewed(topic: Topic) = mutableState.update { it.copy(topic = topic) }

    /** A topic from an event: what the member follows or has read stays as the page had it. */
    private fun keepViewerFields(topic: Topic) =
        mutableState.update { state ->
            val old = state.topic
            state.copy(
                topic =
                    old?.let {
                        topic.copy(
                            followed = it.followed,
                            muted = it.muted,
                            unreadCount = it.unreadCount,
                            mentionState = it.mentionState,
                            // A conversation keeps the name it was given (PC-01).
                            title = if (topic.private) it.title else topic.title,
                        )
                    } ?: topic,
            )
        }

    private fun openingOf(
        window: MessageWindow,
        messages: List<Message>,
    ): Message? = window.firstMessage?.takeIf { first -> messages.firstOrNull()?.id != first.id }

    private companion object {
        /** The read position moves once scrolling has stopped this long (TV-06). */
        const val READ_SETTLE_MS = 1_000L
    }
}
