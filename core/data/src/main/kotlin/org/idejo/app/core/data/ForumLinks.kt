// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.idejo.app.core.api.getOrNull

/** Where an address on a forum leads in the app (NT-04). */
sealed interface LinkTarget {
    data object Home : LinkTarget

    data object Unread : LinkTarget

    data object Conversations : LinkTarget

    /** `/c/{slug}` (DL-02). */
    data class Category(
        val id: String,
    ) : LinkTarget

    /** `/u/{id}`: the user's card (DL-02). */
    data class User(
        val id: String,
    ) : LinkTarget

    /** An address the app has no page for, or off the forum: a Custom Tab (DL-02). */
    data object External : LinkTarget

    /** A topic or conversation; [messageId] to open at a message, else where one left off. */
    data class Topic(
        val id: String,
        val messageId: String? = null,
        val private: Boolean = false,
    ) : LinkTarget
}

/**
 * Where [url] leads on [session]'s forum (NT-04, DL-02): `/`, `/c/{slug}`,
 * `/c/{slug}/t/{id}-{slug}` and `/t/{id}`, `/m/{id}` (where the member left off with
 * `?unread`, else at the message), `/u/{id}`, `/p/{id}` and `/p`, `/unread`; on the inbox,
 * `/chat…` its chats. Anything else, and any address off the forum, is [LinkTarget.External].
 */
suspend fun linkTarget(
    session: ForumSession,
    url: String,
    inbox: Boolean = false,
): LinkTarget {
    val link = url.toHttpUrlOrNull() ?: return LinkTarget.External
    val forum = session.origin.toHttpUrlOrNull() ?: return LinkTarget.External
    val path = link.under(forum) ?: return LinkTarget.External
    val unread = "unread" in link.queryParameterNames
    return pathTarget(session, path, unread, inbox) ?: LinkTarget.External
}

private suspend fun pathTarget(
    session: ForumSession,
    path: List<String>,
    unread: Boolean,
    inbox: Boolean,
): LinkTarget? {
    val id = path.getOrNull(1)
    return when (path.firstOrNull()) {
        "c" -> category(session, path)
        "m" -> id?.let { message(session, it, unread) }
        "chat" -> LinkTarget.Home.takeIf { inbox }
        else -> plainTarget(path)
    }
}

/** The paths read without asking the forum. */
private fun plainTarget(path: List<String>): LinkTarget? {
    val id = path.getOrNull(1)
    return when (path.firstOrNull()) {
        null -> LinkTarget.Home
        "t" -> id?.let { LinkTarget.Topic(it) }
        "u" -> path.single(1)?.let { LinkTarget.User(it) }
        "p" -> id?.let { LinkTarget.Topic(it, private = true) } ?: LinkTarget.Conversations
        "unread" -> LinkTarget.Unread
        else -> null
    }
}

/** The segment at [index] when it is the last one. */
private fun List<String>.single(index: Int): String? =
    getOrNull(index)?.takeIf { size == index + 1 }

/** `/c/{slug}`, or `/c/{slug}/t/{id}-{slug}`: the topic's id comes before its slug. */
private suspend fun category(
    session: ForumSession,
    path: List<String>,
): LinkTarget? {
    if (path.getOrNull(2) == "t") {
        return path.getOrNull(TOPIC_SEGMENT)?.let { LinkTarget.Topic(topicId(it)) }
    }
    if (path.size != 2) return null
    val slug = path[1]
    val categories = session.cached("categories") { categories() }.getOrNull() ?: return null
    return categories.firstOrNull { it.slug == slug }?.let { LinkTarget.Category(it.id) }
}

/** `/c/{slug}/t/{id}-{slug}`: the topic's segment. */
private const val TOPIC_SEGMENT = 3

private val UUID = Regex("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")

/** `{id}-{slug}`, the id a UUID (with its own dashes) or a word without one. */
internal fun topicId(segment: String): String =
    UUID.find(segment)?.value ?: segment.substringBefore('-')

/** The origin of the forum held, or the inbox, that [url] is on; null for any other address. */
fun AppState.originOf(url: String): String? {
    val link = url.toHttpUrlOrNull() ?: return null
    return (forums + listOfNotNull(inbox))
        .firstOrNull { record -> record.origin.toHttpUrlOrNull()?.let { link.under(it) } != null }
        ?.origin
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

/**
 * [url] opened on the forum held, or the inbox, it is on (DL-01, DL-03): its page waits as a
 * [PendingLink] and the app switches there, once launched. False for an address on none.
 */
suspend fun SessionManager.openAt(
    url: String,
    links: PendingLinks,
): Boolean {
    launch.first { it !is Launch.Loading }
    val state = state.first()
    val origin = state.originOf(url) ?: return false
    links.offer(PendingLink(origin, url))
    when {
        active?.origin == origin -> Unit
        state.inbox?.origin == origin -> openInbox()
        else -> enter(origin)
    }
    return true
}

/** A forum address to open once the forum is on screen: a notification tapped (NT-04). */
data class PendingLink(
    val origin: String,
    val url: String,
    /** A topic tapped in the inbox's Topics: Back from it returns to the inbox (IB-08). */
    val fromInbox: Boolean = false,
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
