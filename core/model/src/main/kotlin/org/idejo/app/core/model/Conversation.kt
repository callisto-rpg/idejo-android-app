// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A private conversation (`GET /conversations`, PC-01): a topic only its participants read,
 * with no category and no slow mode. Its id works on the topic paths with the `private`
 * scope; its channel is `conversation:<id>`.
 */
@Serializable
data class Conversation(
    val id: String,
    val uri: String? = null,
    val url: String? = null,
    /** The other participants. */
    val with: List<Person> = emptyList(),
    @SerialName("unread_count") val unreadCount: Int = 0,
    @SerialName("last_message") val lastMessage: ConversationLast? = null,
    @SerialName("created_at") val createdAt: Time? = null,
    @SerialName("last_activity_at") val lastActivityAt: Time? = null,
) {
    /** As the web names it: the other participants' names. */
    val name: String get() = with.joinToString(", ") { it.displayName }
}

/** A conversation's latest message, as its card shows it. */
@Serializable
data class ConversationLast(
    val id: String,
    @SerialName("author_name") val authorName: String? = null,
    val excerpt: String? = null,
    @SerialName("opened_at") val openedAt: Time? = null,
)

/** `POST /conversations`: starts or finds the conversation with [userId] (PC-03). */
@Serializable
data class ConversationStart(
    @SerialName("user_id") val userId: String,
)

/** `PATCH /me`'s private messages choice (PC-03); other fields stay as they are. */
@Serializable
data class PrivateMessagesChoice(
    @SerialName("accepts_private_messages") val acceptsPrivateMessages: Boolean,
)
