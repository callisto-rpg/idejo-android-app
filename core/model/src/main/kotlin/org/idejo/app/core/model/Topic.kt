// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Why a topic has a dot (SH-32), strongest last. Unknown values read as [None] (SY-07). */
@Serializable
enum class MentionState {
    @SerialName("none")
    None,

    @SerialName("new")
    New,

    @SerialName("role")
    Role,

    @SerialName("reply")
    Reply,

    @SerialName("mention")
    Mention,
}

@Serializable
data class Topic(
    val id: String,
    val title: String,
    val slug: String? = null,
    val uri: String? = null,
    val url: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    val private: Boolean = false,
    val tags: List<Tag> = emptyList(),
    val author: Person? = null,
    val topical: Boolean = false,
    val icon: String? = null,
    @SerialName("created_at") val createdAt: Time? = null,
    @SerialName("last_activity_at") val lastActivityAt: Time? = null,
    @SerialName("messages_count") val messagesCount: Int = 0,
    @SerialName("unread_count") val unreadCount: Int = 0,
    @SerialName("mention_state") val mentionState: MentionState = MentionState.None,
    val followed: Boolean = false,
    val muted: Boolean = false,
    val locked: Boolean = false,
    val archived: Boolean = false,
    val pinned: Boolean = false,
    val inactive: Boolean = false,
    @SerialName("followed_by_everyone") val followedByEveryone: Boolean = false,
    @SerialName("continues_from") val continuesFrom: MessageRef? = null,
    /** The opening message, for its first words (TV-22's topic card). */
    @SerialName("first_message_id") val firstMessageId: String? = null,
    /** The reader's last message here, not deleted: the jump button's (SH-04, IX-13). */
    @SerialName("my_last_message_id") val myLastMessageId: String? = null,
    /** From `GET /me/followed` (IX-02): unread @ and + mentions of the member. */
    @SerialName("mentions_count") val mentionsCount: Int = 0,
    /** From `GET /me/followed` (IX-02): others' reactions to the member's messages since read. */
    @SerialName("reactions_count") val reactionsCount: Int = 0,
    /** With `expanded=true` (BR-03). */
    val preview: TopicPreview? = null,
)

@Serializable
data class MessageRef(
    @SerialName("message_id") val messageId: String,
)

/** The Expanded view of a topic (BR-03). */
@Serializable
data class TopicPreview(
    @SerialName("first_excerpt") val firstExcerpt: String? = null,
    val images: List<String> = emptyList(),
    @SerialName("images_count") val imagesCount: Int = 0,
    val latest: List<LatestMessage> = emptyList(),
    val writers: List<Person> = emptyList(),
    @SerialName("writers_count") val writersCount: Int = 0,
)

@Serializable
data class LatestMessage(
    @SerialName("message_id") val messageId: String,
    @SerialName("author_name") val authorName: String,
    val excerpt: String? = null,
)
