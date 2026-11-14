// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.data.TopicReader
import org.idejo.app.core.data.TopicState
import org.idejo.app.core.model.Emoji
import javax.inject.Inject

/** The topic page: its messages, and what draws them (the forum's address and emoji). */
data class TopicUi(
    val topic: TopicState = TopicState(),
    val origin: String = "",
    val emoji: List<Emoji> = emptyList(),
)

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

        val state: StateFlow<TopicUi> =
            combine(
                reader?.state ?: flowOf(TopicState(loading = false, gone = true)),
                session?.server ?: flowOf(null),
            ) { topic, server ->
                TopicUi(topic, session?.origin.orEmpty(), server?.emoji.orEmpty())
            }.stateIn(viewModelScope, SharingStarted.Eagerly, TopicUi())

        init {
            reader?.start()
        }

        fun retry() = reader?.load()

        fun earlier() = reader?.earlier()

        fun later() = reader?.later()

        fun end() = reader?.end()

        fun seen(messageId: String) = reader?.seen(messageId)

        fun unreadShown() = reader?.unreadShown()

        fun follow(followed: Boolean) = reader?.follow(followed)

        override fun onCleared() {
            reader?.stop()
        }
    }
