// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.idejo.app.core.realtime.ForumEvents
import org.idejo.app.core.realtime.IdejoEvent
import org.idejo.app.core.realtime.PhoenixSocket
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The inbox live (IB-05): one socket per connected forum, each with its own token, listening
 * to its `user:me`; `unread.changed` or a notification reads that forum's rows again
 * ([InboxTopics.refresh]), once a burst of events is over. Open only while the inbox is on
 * screen and the app in the foreground ([ForegroundSockets]).
 */
class InboxSockets(
    private val sessions: SessionManager,
    private val topics: InboxTopics,
    private val scope: CoroutineScope,
    private val settle: Duration = 300.milliseconds,
) {
    private class Live(
        val socket: PhoenixSocket,
        val listening: Job,
    )

    private val lock = Mutex()
    private val live = mutableMapOf<String, Live>()
    private val pending = mutableMapOf<String, Job>()

    /** Whether a socket is open to the forum at [origin]. */
    fun isOpen(origin: String): Boolean = origin in live

    /** A socket to each connected forum not yet open. */
    suspend fun open() =
        lock.withLock {
            val origins =
                sessions.state
                    .first()
                    .forums
                    .filter { it.sealedToken != null }
                    .map { it.origin }
            (live.keys - origins.toSet()).toList().forEach { close(it) }
            for (origin in origins - live.keys) {
                val socket = sessions.socketFor(origin, scope) ?: continue
                val user = ForumEvents(socket).user()
                val listening =
                    scope.launch {
                        user.events.collect { event ->
                            if (event is IdejoEvent.UnreadChanged ||
                                event is IdejoEvent.Notification
                            ) {
                                refreshSoon(origin)
                            }
                        }
                    }
                user.join()
                socket.connect()
                live[origin] = Live(socket, listening)
            }
        }

    /** Every socket closed. */
    suspend fun close() =
        lock.withLock {
            live.keys.toList().forEach { close(it) }
        }

    private fun close(origin: String) {
        live.remove(origin)?.let {
            it.listening.cancel()
            it.socket.disconnect()
        }
        synchronized(pending) { pending.remove(origin)?.cancel() }
    }

    private fun refreshSoon(origin: String) {
        synchronized(pending) {
            pending[origin]?.cancel()
            pending[origin] =
                scope.launch {
                    delay(settle)
                    topics.refresh(origin)
                }
        }
    }
}
