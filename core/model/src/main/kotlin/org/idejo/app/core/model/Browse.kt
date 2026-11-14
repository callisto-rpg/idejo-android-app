// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /unread` (BR-06): followed topics first, then the others a page at a time. */
@Serializable
data class Unread(
    val following: List<Topic> = emptyList(),
    val others: Page<Topic> = Page(),
    val counter: UnreadCounter = UnreadCounter(),
)

/** The **Unread** button's count and the strongest reason (`mention`, `reply`, `role`, `new`). */
@Serializable
data class UnreadCounter(
    val count: Int = 0,
    val level: String? = null,
)

/** A message found by `GET /search` (BR-05); [excerpt] marks the matches. */
@Serializable
data class SearchHit(
    @SerialName("message_id") val messageId: String,
    @SerialName("topic_id") val topicId: String,
    @SerialName("topic_title") val topicTitle: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("author_name") val authorName: String,
    val url: String? = null,
    @SerialName("opened_at") val openedAt: Time? = null,
    val excerpt: List<Segment> = emptyList(),
) {
    @Serializable
    data class Segment(
        val text: String,
        val match: Boolean = false,
    )
}

/**
 * A person's card (BR-09, `GET /users/{id}`). [ignoredByMe] is null when they cannot be
 * ignored.
 */
@Serializable
data class UserCard(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val username: String? = null,
    @SerialName("user_tag") val userTag: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("avatar_dark_url") val avatarDarkUrl: String? = null,
    val colour: String? = null,
    val roles: List<Role> = emptyList(),
    @SerialName("bio_md") val bioMd: String? = null,
    @SerialName("joined_at") val joinedAt: Time? = null,
    @SerialName("ignored_by_me") val ignoredByMe: Boolean? = null,
    @SerialName("may_message") val mayMessage: Boolean = false,
    val deleted: Boolean = false,
    val system: Boolean = false,
)

/** A warning not acknowledged yet (TV-11, `GET /me/warnings`). */
@Serializable
data class Warning(
    val id: String,
    val reason: String,
    @SerialName("message_id") val messageId: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("created_at") val createdAt: Time? = null,
)

/** A `!` command available in a topic (BR-08, `GET /topics/{id}/commands`). */
@Serializable
data class Command(
    val name: String,
    val kind: String? = null,
    val sender: String? = null,
    val argument: String? = null,
    val block: Boolean? = null,
)

/** `PUT /topics/{id}/read`: read up to [messageId], or the whole topic when null (TV-06). */
@Serializable
data class ReadPosition(
    @SerialName("message_id") val messageId: String? = null,
)

/** `PUT /polls/{id}/vote`: the member's answers; none takes the vote back (TV-27). */
@Serializable
data class Vote(
    @SerialName("option_ids") val optionIds: List<String>,
)

/** `PATCH /topics/{id}`: a new title (TV-10). */
@Serializable
data class TopicUpdate(
    val title: String? = null,
    @SerialName("tag_ids") val tagIds: List<String>? = null,
)
