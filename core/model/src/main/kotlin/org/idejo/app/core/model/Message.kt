// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class MessageState {
    @SerialName("open")
    Open,

    @SerialName("closed")
    Closed,

    @SerialName("deleted")
    Deleted,

    /** A state this version of the app does not know (SY-07). */
    Unknown,
}

/**
 * A message (API guide §5): its Markdown source and the structured objects beside it, so
 * the app never parses idejo's extensions to know what it holds, and never gets HTML.
 */
@Serializable
data class Message(
    val id: String,
    @SerialName("topic_id") val topicId: String,
    val author: Person,
    val state: MessageState = MessageState.Unknown,
    val uri: String? = null,
    val url: String? = null,
    @SerialName("deleted_by") val deletedBy: String? = null,
    val spoiler: Spoiler? = null,
    val reactions: List<Reaction> = emptyList(),
    val edited: Boolean = false,
    @SerialName("moderator_edited") val moderatorEdited: Boolean = false,
    @SerialName("pinned_at") val pinnedAt: Time? = null,
    @SerialName("versions_count") val versionsCount: Int? = null,
    val scheduled: Boolean = false,
    val held: Boolean = false,
    val anonymous: Boolean = false,
    @SerialName("continues_topic_id") val continuesTopicId: String? = null,
    @SerialName("continued_in") val continuedIn: List<Continuation> = emptyList(),
    /** The change cursor (catch-up, API guide §7). */
    val cursor: Long = 0,
    @SerialName("opened_at") val openedAt: Time? = null,
    @SerialName("closed_at") val closedAt: Time? = null,
    @SerialName("updated_at") val updatedAt: Time? = null,
    @SerialName("body_md") val bodyMd: String? = null,
    val mentions: List<Mention> = emptyList(),
    val images: List<Image> = emptyList(),
    @SerialName("inline_spoilers") val inlineSpoilers: Boolean = false,
    @SerialName("replies_to") val repliesTo: List<ReplyTo> = emptyList(),
    @SerialName("forwarded_from") val forwardedFrom: Forwarded? = null,
    val poll: Poll? = null,
    val plugins: List<PluginBlock> = emptyList(),
)

@Serializable
data class Spoiler(
    val overlay: String? = null,
)

@Serializable
data class Continuation(
    @SerialName("topic_id") val topicId: String,
    val title: String,
)

/** An inline link: `type` is `user`, `role`, `topic`, `category`, `tag` or one added later. */
@Serializable
data class Mention(
    val type: String,
    val text: String,
    val id: String? = null,
    val slug: String? = null,
    val name: String? = null,
)

@Serializable
data class Image(
    val url: String,
    @SerialName("upload_id") val uploadId: String? = null,
    val alt: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class ReplyTo(
    @SerialName("message_id") val messageId: String,
    @SerialName("author_name") val authorName: String,
    val excerpt: String? = null,
    val inline: Boolean = false,
)

/** `visible: false`: "a message forwarded from a topic you cannot view". */
@Serializable
data class Forwarded(
    @SerialName("message_id") val messageId: String,
    val visible: Boolean = false,
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("body_md") val bodyMd: String? = null,
    val excerpt: String? = null,
    @SerialName("topic_id") val topicId: String? = null,
    @SerialName("topic_title") val topicTitle: String? = null,
)

@Serializable
data class Poll(
    val id: String,
    val question: String,
    val multiple: Boolean = false,
    @SerialName("hidden_until_closed") val hiddenUntilClosed: Boolean = false,
    @SerialName("closes_at") val closesAt: Time? = null,
    val closed: Boolean = false,
    @SerialName("results_shown") val resultsShown: Boolean = false,
    val total: Int? = null,
    @SerialName("my_votes") val myVotes: List<String> = emptyList(),
    val options: List<PollOption> = emptyList(),
)

/** [count] is null while results are hidden. */
@Serializable
data class PollOption(
    val id: String,
    val text: String,
    val emoji: String? = null,
    val colour: String? = null,
    val count: Int? = null,
)

@Serializable
data class PluginBlock(
    val command: String,
    val block: Boolean = true,
    val params: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class Reaction(
    val emoji: String,
    val name: String,
    val count: Int,
    val char: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val mine: Boolean = false,
)

/** `GET /topics/{id}/messages`: a window, the first message always included. */
@Serializable
data class MessageWindow(
    val data: List<Message> = emptyList(),
    @SerialName("first_message") val firstMessage: Message? = null,
    @SerialName("first_unread") val firstUnread: UnreadMark? = null,
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("prev_cursor") val prevCursor: String? = null,
    val cursor: Long = 0,
    val total: Int = 0,
)

@Serializable
data class UnreadMark(
    @SerialName("message_id") val messageId: String,
    val count: Int = 0,
)

/** `GET /topics/{id}/changes`: messages changed since a cursor, oldest first. */
@Serializable
data class Changes(
    val data: List<Message> = emptyList(),
    val cursor: Long = 0,
    val more: Boolean = false,
    val topic: Topic? = null,
)
