// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.realtime

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import org.idejo.app.core.api.IdejoJson
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.Poll
import org.idejo.app.core.model.Reaction
import org.idejo.app.core.model.Time
import org.idejo.app.core.model.Topic

/** idejo's channel events (API guide §7), typed. Anything else is [Unknown] (SY-07). */
sealed interface IdejoEvent {
    // topic:<id> and conversation:<id>

    /** `message.opened`, `.appended`, `.edited`, `.closed`, `.deleted`: the whole message. */
    data class MessageChanged(
        val change: Change,
        val message: Message,
        val cursor: Long,
    ) : IdejoEvent

    enum class Change { Opened, Appended, Edited, Closed, Deleted }

    data class MessageForked(
        val message: Message,
    ) : IdejoEvent

    data class ReactionsChanged(
        val messageId: String,
        val reactions: List<Reaction>,
    ) : IdejoEvent

    data class PollUpdated(
        val messageId: String,
        val poll: Poll?,
    ) : IdejoEvent

    /** `topic.updated`, `topic.moved`, `topic.archived` on the topic's own channel. */
    data class TopicChanged(
        val event: String,
        val topic: Topic,
    ) : IdejoEvent

    /** `messages.moved`: load the window again. */
    data class MessagesMoved(
        val topicId: String,
    ) : IdejoEvent

    /** The member lost the right to view: the channel is left. */
    data class Forbidden(
        val reason: String?,
    ) : IdejoEvent

    /**
     * After a rejoin: fetch `GET /topics/{id}/changes?since=[since]`, then call
     * [TopicStream.caughtUp] (API guide §7). [since] is null if no cursor was ever seen.
     */
    data class CatchUp(
        val since: Long?,
    ) : IdejoEvent

    // category:<id>

    data class TopicCreated(
        val topic: Topic,
    ) : IdejoEvent

    data class TopicActivity(
        val topicId: String,
        val lastActivityAt: java.time.Instant?,
        val messagesCount: Int,
    ) : IdejoEvent

    data class TopicMovedOut(
        val topicId: String,
        val from: String?,
        val to: String?,
    ) : IdejoEvent

    data class TopicArchived(
        val topicId: String,
    ) : IdejoEvent

    // user:me

    @Serializable
    data class Notification(
        val kind: String,
        @SerialName("message_id") val messageId: String? = null,
        @SerialName("topic_id") val topicId: String? = null,
        @SerialName("topic_title") val topicTitle: String? = null,
        @SerialName("author_name") val authorName: String? = null,
        val excerpt: String? = null,
        @SerialName("emoji_url") val emojiUrl: String? = null,
        val reason: String? = null,
        @SerialName("warning_id") val warningId: String? = null,
    ) : IdejoEvent

    data class UnreadChanged(
        val count: Int,
        val level: String?,
    ) : IdejoEvent

    data class ConversationUpdated(
        val conversationId: String,
    ) : IdejoEvent

    data class ScheduledPosted(
        val messageId: String,
        val topicId: String?,
    ) : IdejoEvent

    /** This device was signed out (SI-09); the socket closes. */
    data class TokenRevoked(
        val tokenId: String?,
    ) : IdejoEvent

    data class SettingsChanged(
        val keys: List<String>,
    ) : IdejoEvent

    data class Unknown(
        val event: String,
        val payload: JsonElement,
    ) : IdejoEvent
}

/** Which kind of channel an event came on: the same name means different things. */
enum class ChannelKind {
    User,
    Topic,
    Category,
    ;

    companion object {
        fun of(topic: String): ChannelKind =
            when {
                topic == "user:me" -> User
                topic.startsWith("category:") -> Category
                else -> Topic
            }
    }
}

object IdejoEvents {
    /** [event] with [payload] heard on a [kind] channel. */
    @Suppress("CyclomaticComplexMethod") // One branch per event of the guide's table.
    fun parse(
        kind: ChannelKind,
        event: String,
        payload: JsonElement,
    ): IdejoEvent =
        try {
            when (kind) {
                ChannelKind.Topic -> topic(event, payload)
                ChannelKind.Category -> category(event, payload)
                ChannelKind.User -> user(event, payload)
            } ?: IdejoEvent.Unknown(event, payload)
        } catch (_: SerializationException) {
            IdejoEvent.Unknown(event, payload)
        } catch (_: IllegalArgumentException) {
            IdejoEvent.Unknown(event, payload)
        }

    private fun topic(
        event: String,
        payload: JsonElement,
    ): IdejoEvent? {
        CHANGES[event]?.let { change ->
            val body = payload.read(MessageBody.serializer())
            return IdejoEvent.MessageChanged(
                change,
                body.message,
                body.cursor ?: body.message.cursor,
            )
        }
        return when (event) {
            "message.forked" -> {
                IdejoEvent.MessageForked(payload.read(MessageBody.serializer()).message)
            }

            "reaction.changed" -> {
                payload.read(ReactionsBody.serializer()).let {
                    IdejoEvent.ReactionsChanged(it.messageId, it.reactions)
                }
            }

            "poll.updated" -> {
                payload
                    .read(
                        PollBody.serializer(),
                    ).let { IdejoEvent.PollUpdated(it.messageId, it.poll) }
            }

            "topic.updated", "topic.moved", "topic.archived" -> {
                IdejoEvent.TopicChanged(event, payload.read(Topic.serializer()))
            }

            "messages.moved" -> {
                IdejoEvent.MessagesMoved(payload.read(TopicIdBody.serializer()).topicId)
            }

            "forbidden" -> {
                IdejoEvent.Forbidden(payload.read(ReasonBody.serializer()).reason)
            }

            else -> {
                null
            }
        }
    }

    private fun category(
        event: String,
        payload: JsonElement,
    ): IdejoEvent? =
        when (event) {
            "topic.created" -> {
                IdejoEvent.TopicCreated(payload.read(Topic.serializer()))
            }

            "topic.activity" -> {
                payload.read(ActivityBody.serializer()).let {
                    IdejoEvent.TopicActivity(it.topicId, it.lastActivityAt, it.messagesCount)
                }
            }

            "topic.moved" -> {
                payload
                    .read(
                        MovedBody.serializer(),
                    ).let { IdejoEvent.TopicMovedOut(it.topicId, it.from, it.to) }
            }

            "topic.archived" -> {
                IdejoEvent.TopicArchived(payload.read(TopicIdBody.serializer()).topicId)
            }

            "forbidden" -> {
                IdejoEvent.Forbidden(payload.read(ReasonBody.serializer()).reason)
            }

            else -> {
                null
            }
        }

    private fun user(
        event: String,
        payload: JsonElement,
    ): IdejoEvent? =
        when (event) {
            "notification" -> {
                payload.read(IdejoEvent.Notification.serializer())
            }

            "unread.changed" -> {
                payload
                    .read(
                        UnreadBody.serializer(),
                    ).let { IdejoEvent.UnreadChanged(it.count, it.level) }
            }

            "conversation.updated" -> {
                IdejoEvent.ConversationUpdated(
                    payload.read(ConversationBody.serializer()).conversationId,
                )
            }

            "scheduled.posted" -> {
                payload.read(ScheduledBody.serializer()).let {
                    IdejoEvent.ScheduledPosted(it.messageId, it.topicId)
                }
            }

            "token.revoked" -> {
                IdejoEvent.TokenRevoked(payload.read(TokenBody.serializer()).tokenId)
            }

            "settings.changed" -> {
                IdejoEvent.SettingsChanged(payload.read(SettingsBody.serializer()).keys)
            }

            else -> {
                null
            }
        }

    private fun <T> JsonElement.read(serializer: KSerializer<T>): T =
        IdejoJson.decodeFromJsonElement(serializer, this)

    private val CHANGES =
        mapOf(
            "message.opened" to IdejoEvent.Change.Opened,
            "message.appended" to IdejoEvent.Change.Appended,
            "message.edited" to IdejoEvent.Change.Edited,
            "message.closed" to IdejoEvent.Change.Closed,
            "message.deleted" to IdejoEvent.Change.Deleted,
        )
}

@Serializable
private class MessageBody(
    val message: Message,
    val cursor: Long? = null,
)

@Serializable
private class ReactionsBody(
    @SerialName("message_id") val messageId: String,
    val reactions: List<Reaction> = emptyList(),
)

@Serializable
private class PollBody(
    @SerialName("message_id") val messageId: String,
    val poll: Poll? = null,
)

@Serializable
private class TopicIdBody(
    @SerialName("topic_id") val topicId: String,
)

@Serializable
private class ReasonBody(
    val reason: String? = null,
)

@Serializable
private class ActivityBody(
    @SerialName("topic_id") val topicId: String,
    @SerialName("last_activity_at") val lastActivityAt: Time? = null,
    @SerialName("messages_count") val messagesCount: Int = 0,
)

@Serializable
private class MovedBody(
    @SerialName("topic_id") val topicId: String,
    val from: String? = null,
    val to: String? = null,
)

@Serializable
private class UnreadBody(
    val count: Int = 0,
    val level: String? = null,
)

@Serializable
private class ConversationBody(
    @SerialName("conversation_id") val conversationId: String,
)

@Serializable
private class ScheduledBody(
    @SerialName("message_id") val messageId: String,
    @SerialName("topic_id") val topicId: String? = null,
)

@Serializable
private class TokenBody(
    @SerialName("token_id") val tokenId: String? = null,
)

@Serializable
private class SettingsBody(
    val keys: List<String> = emptyList(),
)
