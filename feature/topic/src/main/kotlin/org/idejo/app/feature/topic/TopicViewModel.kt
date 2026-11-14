// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.idejo.app.core.api.getOrNull
import org.idejo.app.core.data.Access
import org.idejo.app.core.data.ContinuedFrom
import org.idejo.app.core.data.Glow
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.data.TopicReader
import org.idejo.app.core.data.TopicState
import org.idejo.app.core.data.continuedFrom
import org.idejo.app.core.data.mentionGlows
import org.idejo.app.core.model.Emoji
import org.idejo.app.core.model.Me
import javax.inject.Inject

/** The topic page: its messages, and what draws them (the forum's address and emoji). */
data class TopicUi(
    val topic: TopicState = TopicState(),
    val origin: String = "",
    val emoji: List<Emoji> = emptyList(),
    /** A member is signed in (polls take votes). */
    val signedIn: Boolean = false,
    /** The member, whose mentions glow and pulse (TV-07). */
    val me: Me? = null,
    /** The message the topic was started from (idejo TP-09), when the member may view it. */
    val continuedFrom: ContinuedFrom? = null,
) {
    /** The unread messages naming the member, glowing once reached (TV-07). */
    val glows: Map<String, Glow> by lazy { mentionGlows(topic.messages, topic.glowFrom, me) }
}

/**
 * The topic page (TV-01 to TV-12): the route's `id` is the topic, its `messageId` the message
 * to land on. The topic's channel stays joined while the page is on the back stack (TV-12).
 */
@HiltViewModel
class TopicViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        sessions: SessionManager,
    ) : ViewModel() {
        private val session = sessions.active
        private val reader: TopicReader? =
            session?.let {
                TopicReader(
                    it,
                    checkNotNull(savedState["id"]),
                    viewModelScope,
                    landing = savedState["messageId"],
                )
            }

        private val me = MutableStateFlow<Me?>(null)
        private val continuedFrom = MutableStateFlow<ContinuedFrom?>(null)

        val state: StateFlow<TopicUi> =
            combine(
                reader?.state ?: flowOf(TopicState(loading = false, gone = true)),
                session?.server ?: flowOf(null),
                session?.access ?: flowOf(null),
                me,
                continuedFrom,
            ) { topic, server, access, me, from ->
                TopicUi(
                    topic,
                    session?.origin.orEmpty(),
                    server?.emoji.orEmpty() + server?.defaultReactions.orEmpty(),
                    signedIn = access == Access.SignedIn,
                    me = me.takeIf { access == Access.SignedIn },
                    continuedFrom = from,
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, TopicUi())

        init {
            reader?.start()
            reader?.let { topics ->
                viewModelScope.launch {
                    val topic = topics.state.mapNotNull { it.topic }.first()
                    continuedFrom.value = session?.continuedFrom(topic)
                }
            }
            if (session?.access?.value == Access.SignedIn) {
                viewModelScope.launch { me.value = session.call { me() }.getOrNull() }
            }
        }

        fun retry() = reader?.load()

        fun earlier() = reader?.earlier()

        fun later() = reader?.later()

        fun end() = reader?.end()

        fun seen(messageId: String) = reader?.seen(messageId)

        fun unreadShown() = reader?.unreadShown()

        fun follow(followed: Boolean) = reader?.follow(followed)

        fun vote(
            messageId: String,
            pollId: String,
            optionIds: List<String>,
        ) = reader?.vote(messageId, pollId, optionIds)

        /** Where message [messageId] is: this topic, another one, or null (not readable). */
        suspend fun reach(messageId: String): String? = reader?.reach(messageId)

        override fun onCleared() {
            reader?.stop()
        }
    }
