// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.model.Topic
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** A followed topic of one of the group's forums, for the inbox's **Topics** (IB-03). */
data class ForumTopic(
    val origin: String,
    val forum: String,
    val logoUrl: String?,
    val topic: Topic,
)

/** The connected forums as the inbox shows them (IB-02, IB-03, IB-04). */
data class InboxTopicsState(
    /** Every connected forum's followed topics, the latest activity first. */
    val topics: List<ForumTopic> = emptyList(),
    /**
     * Each connected forum's new messages, by origin: those of its unread topics (`GET
     * /unread`'s followed topics and first page of others; idejo's **Unread** counter counts
     * replies and mentions only).
     */
    val unread: Map<String, Int> = emptyMap(),
    /** Forums that did not answer in time: "Couldn't reach {name}" (IB-04). */
    val unreachable: List<String> = emptyList(),
    val loading: Boolean = true,
)

/**
 * The inbox's **Topics** built in the app (IB-04): for each connected forum, with its own
 * token, its followed topics (`GET /me/followed`, IX-02; before idejo 0.3.16 `GET /unread`'s
 * `following`, unread topics only, without reaction counts) and its unread count, all forums
 * at once, each given [patience] (four seconds); one that fails still lets the rest show.
 */
class InboxTopics(
    private val sessions: SessionManager,
    private val patience: Duration = 4.seconds,
) {
    private val mutableState = MutableStateFlow(InboxTopicsState())
    val state: StateFlow<InboxTopicsState> = mutableState.asStateFlow()

    suspend fun refresh() {
        mutableState.update { it.copy(loading = true) }
        val forums =
            sessions.state
                .first()
                .forums
                .filter { it.sealedToken != null }
        val read =
            coroutineScope {
                forums
                    .map { forum ->
                        async { forum to withTimeoutOrNull(patience) { read(forum) } }
                    }.awaitAll()
            }
        mutableState.value =
            InboxTopicsState(
                topics =
                    read
                        .flatMap { (forum, found) ->
                            found?.topics.orEmpty().map {
                                ForumTopic(forum.origin, forum.name, logoOf(forum), it)
                            }
                        }.sortedByDescending { it.topic.lastActivityAt ?: it.topic.createdAt },
                unread =
                    read
                        .mapNotNull { (forum, found) ->
                            found?.let { forum.origin to it.unread }
                        }.toMap(),
                unreachable = read.filter { it.second == null }.map { it.first.name },
                loading = false,
            )
    }

    /** Reads the forum at [origin] again, the others' rows kept (IB-05). */
    suspend fun refresh(origin: String) {
        val forum =
            sessions.state
                .first()
                .forum(origin)
                ?.takeIf { it.sealedToken != null } ?: return
        val found = withTimeoutOrNull(patience) { read(forum) } ?: return
        mutableState.update { state ->
            state.copy(
                topics =
                    (
                        state.topics.filterNot { it.origin == origin } +
                            found.topics.map {
                                ForumTopic(
                                    forum.origin,
                                    forum.name,
                                    logoOf(forum),
                                    it,
                                )
                            }
                    ).sortedByDescending { it.topic.lastActivityAt ?: it.topic.createdAt },
                unread = state.unread + (origin to found.unread),
                unreachable = state.unreachable - forum.name,
            )
        }
    }

    private class Found(
        val unread: Int,
        val topics: List<Topic>,
    )

    private suspend fun read(forum: ForumRecord): Found? =
        coroutineScope {
            val unread = async { sessions.signedIn(forum.origin) { unread() } }
            val followed = async { sessions.signedIn(forum.origin) { followed() } }
            val counts = unread.await() as? ApiResult.Success ?: return@coroutineScope null
            val topics =
                when (val result = followed.await()) {
                    is ApiResult.Success -> result.value.data

                    // Before idejo 0.3.16: the unread followed topics only (IX-02).
                    else -> counts.value.following
                }
            val unreadTopics = counts.value.following + counts.value.others.data
            Found(unreadTopics.sumOf { it.unreadCount }, topics)
        }

    private fun logoOf(forum: ForumRecord): String? =
        forum.discovery.logoUrl?.let {
            forum.origin
                .toHttpUrlOrNull()
                ?.resolve(it)
                ?.toString()
        }
}
