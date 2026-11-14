// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import org.idejo.app.core.api.getOrNull
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.MessageState
import org.idejo.app.core.model.Topic

/** The message a topic was started from, and its topic's title (idejo TP-09). */
data class ContinuedFrom(
    val message: Message,
    val topicTitle: String,
)

/**
 * Where [topic] continues from, as the web's header shows it: its source message
 * (`GET /messages/{id}`) and that message's topic (`GET /topics/{id}`). Null when there is
 * none, when the member may not view it, or when a moderator deleted it (as the web).
 */
suspend fun ForumSession.continuedFrom(topic: Topic): ContinuedFrom? {
    val id = topic.continuesFrom?.messageId ?: return null
    val message = call { message(id) }.getOrNull() ?: return null
    if (message.state == MessageState.Deleted && message.deletedBy == "moderator") return null
    val source = call { topic(message.topicId) }.getOrNull() ?: return null
    return ContinuedFrom(message, source.title)
}
