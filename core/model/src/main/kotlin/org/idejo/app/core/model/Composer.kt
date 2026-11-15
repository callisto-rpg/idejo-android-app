// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The composer's state (API guide §6). The countdown is drawn from these; the app never
 * works the rules out itself.
 */
@Serializable
data class ComposerState(
    @SerialName("open_message_id") val openMessageId: String? = null,
    @SerialName("may_start_new_at") val mayStartNewAt: Time? = null,
    @SerialName("held_until") val heldUntil: Time? = null,
    @SerialName("typing_since") val typingSince: Time? = null,
    @SerialName("window_seconds") val windowSeconds: Int = 0,
    @SerialName("unanswered_seconds") val unansweredSeconds: Int = 0,
    @SerialName("reply_allowed_early") val replyAllowedEarly: Boolean = false,
    @SerialName("may_post") val mayPost: Boolean = false,
    val now: Time? = null,
)

/** An Enter (API guide §6): the whole open message as the composer shows it. */
@Serializable
data class Commit(
    @SerialName("body_md") val bodyMd: String,
    @SerialName("break") val breakMessage: Boolean = false,
    val new: Boolean = false,
    @SerialName("reply_to") val replyTo: List<String> = emptyList(),
    @SerialName("forward_of") val forwardOf: String? = null,
)

@Serializable
data class CommitResult(
    val message: Message? = null,
    val composer: ComposerState,
)

/** `PUT /messages/{id}`: the whole text of one's closed message; `finish` ends the edit. */
@Serializable
data class MessageEdit(
    @SerialName("body_md") val bodyMd: String,
    val finish: Boolean = false,
)

/** A topic or private conversation to forward to (`GET /forward-targets`, CO-17). */
@Serializable
data class ForwardTarget(
    val id: String,
    val title: String,
    val url: String? = null,
    val private: Boolean = false,
    val place: String? = null,
)

/** What follows `@ + # / $ &` in the composer (`GET /suggest`, CO-12). */
@Serializable
data class Suggestion(
    /** `user`, `role`, `topic`, `category`, `tag`, `forum`. */
    val kind: String,
    /** What goes after the sign. */
    val insert: String,
    val label: String,
    val id: String? = null,
)

/** `POST /uploads` (CO-14): the image's Markdown, as the web composer inserts it. */
@Serializable
data class Upload(
    @SerialName("upload_id") val uploadId: String,
    val url: String,
    val markdown: String,
    val width: Int? = null,
    val height: Int? = null,
)

/** One of the member's scheduled messages in a topic (CO-20). */
@Serializable
data class Scheduled(
    val id: String,
    @SerialName("post_at") val postAt: Time,
    @SerialName("topic_id") val topicId: String? = null,
    @SerialName("body_md") val bodyMd: String? = null,
)

/** `!schedule` (CO-13): what is not sent yet, posted in [hours] (at most 168). */
@Serializable
data class ScheduleCreate(
    @SerialName("body_md") val bodyMd: String,
    val hours: Double,
    @SerialName("reply_to") val replyTo: String? = null,
)

/** `!poll` (CO-13): under [messageId], or on its own. */
@Serializable
data class PollCreate(
    val question: String,
    val options: List<String>,
    val hours: Double,
    val multiple: Boolean = false,
    val hidden: Boolean = false,
    @SerialName("message_id") val messageId: String? = null,
    @SerialName("reply_to") val replyTo: String? = null,
)

/** `!spoiler` (CO-13), with `!spoiler:overlay:`'s words. */
@Serializable
data class SpoilerSet(
    val overlay: String? = null,
)

/** A plugin's command (CO-13): its block's [params], under [messageId] or on its own. */
@Serializable
data class PluginRun(
    val params: Map<String, String> = emptyMap(),
    @SerialName("message_id") val messageId: String? = null,
    @SerialName("reply_to") val replyTo: String? = null,
)
