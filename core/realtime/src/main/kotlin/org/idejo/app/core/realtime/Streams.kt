// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.realtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A forum's channels (API guide §7), typed. */
class ForumEvents(
    val socket: PhoenixSocket,
) {
    /** `user:me`: notifications, unread counts, `token.revoked`. */
    fun user(): EventStream = EventStream(socket.channel("user:me"))

    fun category(id: String): EventStream = EventStream(socket.channel("category:$id"))

    fun topic(id: String): TopicStream = TopicStream(socket.channel("topic:$id"))

    /** A private conversation (scope `private`). */
    fun conversation(id: String): TopicStream = TopicStream(socket.channel("conversation:$id"))
}

/** One channel's events. Collect [events] before [join]. */
open class EventStream(
    val channel: PhoenixChannel,
) {
    private val kind = ChannelKind.of(channel.topic)

    val state: StateFlow<ChannelState> get() = channel.state

    open val events: Flow<IdejoEvent>
        get() =
            channel.messages.mapNotNull {
                (it as? ChannelMessage.Event)?.let { e ->
                    IdejoEvents.parse(kind, e.event, e.payload)
                }
            }

    fun join() = channel.join()

    fun leave() = channel.leave()
}

/**
 * A topic's (or conversation's) events, with the change cursor kept for catch-up: the
 * join reply's `cursor`, then each message event's. After a rejoin it emits
 * [IdejoEvent.CatchUp] before any event of the new join.
 */
class TopicStream(
    channel: PhoenixChannel,
) : EventStream(channel) {
    private val mutableCursor = MutableStateFlow<Long?>(null)
    val cursor: StateFlow<Long?> = mutableCursor.asStateFlow()

    override val events: Flow<IdejoEvent>
        get() =
            channel.messages.mapNotNull { message ->
                when (message) {
                    is ChannelMessage.Joined -> {
                        joined(message)
                    }

                    is ChannelMessage.Event -> {
                        val event =
                            IdejoEvents.parse(
                                ChannelKind.Topic,
                                message.event,
                                message.payload,
                            )
                        if (event is IdejoEvent.MessageChanged) advance(event.cursor)
                        event
                    }
                }
            }

    /** Changes up to [cursor] were loaded (`changes.cursor`). */
    fun caughtUp(cursor: Long) = advance(cursor)

    private fun joined(message: ChannelMessage.Joined): IdejoEvent? {
        val replyCursor =
            (
                (message.response as? JsonObject)?.get(
                    "cursor",
                ) as? JsonPrimitive
            )?.longOrNull
        if (message.rejoined) return IdejoEvent.CatchUp(mutableCursor.value)
        // The first join: what the window loaded is current up to here.
        if (mutableCursor.value == null && replyCursor != null) advance(replyCursor)
        return null
    }

    private fun advance(cursor: Long) = mutableCursor.update { maxOf(it ?: cursor, cursor) }
}
