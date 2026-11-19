// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.idejo.app.core.data.AppChoices
import org.idejo.app.core.data.ForumTopic
import org.idejo.app.core.data.GroupForums
import org.idejo.app.core.data.InboxTopicChoices
import org.idejo.app.core.data.InboxTopics
import org.idejo.app.core.data.InboxTopicsState
import org.idejo.app.core.data.PendingLink
import org.idejo.app.core.data.PendingLinks
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.data.TopicView
import org.idejo.app.core.model.Me
import javax.inject.Inject

/** The inbox's frame (IB-01) and its **Topics** (IB-03). */
data class InboxState(
    val name: String = "",
    /** The inbox's logo, on its own host. */
    val logoUrl: String? = null,
    val me: Me? = null,
    val topics: InboxTopicsState = InboxTopicsState(),
    val choices: InboxTopicChoices = InboxTopicChoices(),
    /** The connected forums, for **Filter**: origin to name. */
    val forums: Map<String, String> = emptyMap(),
) {
    /** The topics as the toolbar's choices show them. */
    val shown: List<ForumTopic>
        get() =
            topics.topics.filter {
                it.origin !in choices.hidden && (!choices.unreadOnly || it.topic.unreadCount > 0)
            }
}

sealed interface InboxEvent {
    data object Refresh : InboxEvent

    data class View(
        val view: TopicView,
    ) : InboxEvent

    data class UnreadOnly(
        val on: Boolean,
    ) : InboxEvent

    /** **Filter**: the forums whose topics are hidden. */
    data class Hidden(
        val origins: Set<String>,
    ) : InboxEvent

    /** A topic tapped: it opens in its forum (IB-08). */
    data class Open(
        val topic: ForumTopic,
    ) : InboxEvent
}

@HiltViewModel
class InboxViewModel
    @Inject
    constructor(
        private val sessions: SessionManager,
        private val topics: InboxTopics,
        private val choices: AppChoices,
        private val links: PendingLinks,
        group: GroupForums,
    ) : ViewModel() {
        private val inbox = sessions.inbox

        val state: StateFlow<InboxState> =
            combine(
                inbox?.me?.state ?: flowOf(null),
                topics.state,
                choices.inboxTopics,
                group.joined,
            ) { me, read, chosen, joined ->
                val record = inbox?.record?.value
                InboxState(
                    name = record?.name.orEmpty(),
                    logoUrl =
                        record?.discovery?.logoUrl?.let {
                            record.origin
                                .toHttpUrlOrNull()
                                ?.resolve(it)
                                ?.toString()
                        },
                    me = me,
                    topics = read,
                    choices = chosen,
                    forums = joined.filter { it.connected }.associate { it.url to it.name },
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                InboxState(name = inbox?.name.orEmpty()),
            )

        init {
            viewModelScope.launch { inbox?.me?.refresh() }
            refresh()
        }

        fun onEvent(event: InboxEvent) {
            when (event) {
                InboxEvent.Refresh -> refresh()
                is InboxEvent.View -> remember { it.copy(view = event.view) }
                is InboxEvent.UnreadOnly -> remember { it.copy(unreadOnly = event.on) }
                is InboxEvent.Hidden -> remember { it.copy(hidden = event.origins) }
                is InboxEvent.Open -> open(event.topic)
            }
        }

        private fun refresh() {
            viewModelScope.launch { topics.refresh() }
        }

        private fun remember(transform: (InboxTopicChoices) -> InboxTopicChoices) {
            viewModelScope.launch { choices.inboxTopics(transform) }
        }

        /** In its forum, as a tapped notification is (NT-04): the link waits for its screen. */
        private fun open(item: ForumTopic) {
            val url = item.topic.url ?: "${item.origin.trimEnd('/')}/t/${item.topic.id}"
            links.offer(PendingLink(item.origin, url, fromInbox = true))
            viewModelScope.launch { sessions.enter(item.origin) }
        }
    }
