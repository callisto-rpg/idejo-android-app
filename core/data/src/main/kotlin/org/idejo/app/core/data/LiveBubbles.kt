// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.realtime.IdejoEvent

/** What a live notification is about (NT-01), as the web's bubbles tell them apart. */
enum class NotificationKind {
    /** A new message in a followed topic: grey. */
    Followed,

    /** One of the member's roles mentioned: blue. */
    RoleMention,

    /** A reply to the member: orange. */
    Reply,

    /** The member mentioned: red. */
    Mention,

    /** Someone reacted to the member's message: purple. */
    Reaction,
    ;

    companion object {
        /** idejo's `kind`; anything newer reads as a followed topic, as the web's text does. */
        fun of(kind: String): NotificationKind =
            when (kind) {
                "mention" -> Mention
                "reply" -> Reply
                "role_mention" -> RoleMention
                "reaction" -> Reaction
                else -> Followed
            }
    }
}

/** One bubble (NT-01): who did what in which topic, and where a tap leads. */
data class LiveBubble(
    /** Tells bubbles apart, newest highest. */
    val key: Long,
    val kind: NotificationKind,
    val authorName: String,
    val topicTitle: String,
    val excerpt: String?,
    val topicId: String,
    val messageId: String?,
    /** A reaction's emoji, as an image address. */
    val emojiUrl: String?,
)

/**
 * The web's live bubbles for [session]'s member (NT-01): each `notification` of the
 * `user:me` channel but warnings (the warning card shows those), newest first, three at
 * most, none about the topic on screen ([onScreen]). The screen dismisses them: after 10 s,
 * a swipe or ×.
 */
class LiveBubbles(
    private val session: ForumSession,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<List<LiveBubble>>(emptyList())
    val state: StateFlow<List<LiveBubble>> = mutableState.asStateFlow()
    private var listening: Job? = null
    private var next = 0L

    /** The topic shown now; its notifications make no bubble, as on the web. */
    @Volatile var onScreen: String? = null

    fun start() {
        listening?.cancel()
        listening =
            scope.launch {
                session.userEvents.collect { event ->
                    if (event is IdejoEvent.Notification) add(event)
                }
            }
    }

    fun dismiss(key: Long) {
        mutableState.update { bubbles -> bubbles.filterNot { it.key == key } }
    }

    fun close() {
        listening?.cancel()
        mutableState.value = emptyList()
    }

    private fun add(event: IdejoEvent.Notification) {
        val topicId = event.topicId ?: return
        if (event.kind == WARNING || topicId == onScreen) return
        val bubble =
            LiveBubble(
                key = next++,
                kind = NotificationKind.of(event.kind),
                authorName = event.authorName.orEmpty(),
                topicTitle = event.topicTitle.orEmpty(),
                excerpt = event.excerpt?.takeIf { it.isNotBlank() },
                topicId = topicId,
                messageId = event.messageId,
                emojiUrl = event.emojiUrl,
            )
        mutableState.update { (listOf(bubble) + it).take(MOST) }
    }

    private companion object {
        const val WARNING = "warning"
        const val MOST = 3
    }
}
