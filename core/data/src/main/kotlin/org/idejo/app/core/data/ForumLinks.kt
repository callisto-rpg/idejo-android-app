// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.idejo.app.core.api.getOrNull

/** Where an address on a forum leads in the app (NT-04). */
sealed interface LinkTarget {
    data object Home : LinkTarget

    data object Unread : LinkTarget

    data object Conversations : LinkTarget

    /** A topic or conversation; [messageId] to open at a message, else where one left off. */
    data class Topic(
        val id: String,
        val messageId: String? = null,
        val private: Boolean = false,
    ) : LinkTarget
}

/**
 * The pages idejo's pushes open (NT-04): `/m/{id}?unread` where the member left off in its
 * topic, `/m/{id}` at the message, `/t/{id}`, `/p/{id}` and `/p`, `/unread`. Only addresses on
 * [session]'s forum: any other, and any path the app has no page for, opens the home page.
 */
suspend fun linkTarget(
    session: ForumSession,
    url: String,
): LinkTarget {
    val link = url.toHttpUrlOrNull() ?: return LinkTarget.Home
    val forum = session.origin.toHttpUrlOrNull() ?: return LinkTarget.Home
    val path = link.under(forum) ?: return LinkTarget.Home
    val id = path.getOrNull(1)
    return when (path.firstOrNull()) {
        "m" -> id?.let { message(session, it, unread = "unread" in link.queryParameterNames) }
        "t" -> id?.let { LinkTarget.Topic(it) }
        "p" -> id?.let { LinkTarget.Topic(it, private = true) } ?: LinkTarget.Conversations
        "unread" -> LinkTarget.Unread
        else -> null
    } ?: LinkTarget.Home
}

/** The path past the forum's own, when [this] is on [forum]; null otherwise. */
private fun HttpUrl.under(forum: HttpUrl): List<String>? {
    if (scheme != forum.scheme || host != forum.host || port != forum.port) return null
    val base = forum.pathSegments.filter { it.isNotEmpty() }
    val segments = pathSegments.filter { it.isNotEmpty() }
    return if (segments.take(base.size) == base) segments.drop(base.size) else null
}

private suspend fun message(
    session: ForumSession,
    id: String,
    unread: Boolean,
): LinkTarget? {
    val message = session.call { message(id) }.getOrNull() ?: return null
    val private = session.call { topic(message.topicId) }.getOrNull()?.private == true
    return LinkTarget.Topic(
        message.topicId,
        messageId = id.takeUnless { unread },
        private = private,
    )
}

/** A forum address to open once the forum is on screen: a notification tapped (NT-04). */
data class PendingLink(
    val origin: String,
    val url: String,
)

/** The address a tapped notification opens, waiting for its forum's screen. */
class PendingLinks {
    private val mutable = MutableStateFlow<PendingLink?>(null)
    val pending: StateFlow<PendingLink?> = mutable.asStateFlow()

    fun offer(link: PendingLink) {
        mutable.value = link
    }

    /** The link waiting for [origin], taken so it opens once. */
    fun take(origin: String): PendingLink? {
        val link = mutable.value?.takeIf { it.origin == origin } ?: return null
        return link.takeIf { mutable.compareAndSet(it, null) }
    }

    companion object {
        /** The notification's extra naming its forum. */
        const val EXTRA_FORUM = "org.idejo.app.extra.FORUM"
    }
}
