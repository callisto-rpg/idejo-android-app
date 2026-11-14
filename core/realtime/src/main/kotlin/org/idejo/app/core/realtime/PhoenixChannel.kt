// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.realtime

import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

sealed interface ChannelState {
    data object Closed : ChannelState

    data object Joining : ChannelState

    data class Joined(
        val response: JsonElement,
    ) : ChannelState

    /** Lost (socket dropped, server error, no answer): joined again as soon as possible. */
    data object Errored : ChannelState

    /** The join was refused (`not_found`, `insufficient_scope`): not tried again. */
    data class Refused(
        val reason: String?,
    ) : ChannelState

    data object Left : ChannelState
}

/** What a channel hears, in order. */
sealed interface ChannelMessage {
    /** A join succeeded; [rejoined] after a drop, when what was missed must be caught up. */
    data class Joined(
        val response: JsonElement,
        val rejoined: Boolean,
    ) : ChannelMessage

    data class Event(
        val event: String,
        val payload: JsonElement,
    ) : ChannelMessage
}

/**
 * One channel of a [PhoenixSocket]. Collect [messages] before [join]: nothing is replayed.
 */
class PhoenixChannel internal constructor(
    private val socket: PhoenixSocket,
    val topic: String,
    private val params: () -> JsonObject,
) {
    private val mutableState = MutableStateFlow<ChannelState>(ChannelState.Closed)
    val state: StateFlow<ChannelState> = mutableState.asStateFlow()

    private val mutableMessages =
        MutableSharedFlow<ChannelMessage>(extraBufferCapacity = Channel.UNLIMITED)
    val messages: SharedFlow<ChannelMessage> = mutableMessages.asSharedFlow()

    private var wanted = false
    private var joinRef: String? = null
    private var joinedBefore = false
    private var attempts = 0
    private var rejoinJob: Job? = null

    /** Joins now if the socket is open, else as soon as it opens, and after every drop. */
    fun join() {
        socket.post {
            if (!wanted) {
                wanted = true
                if (socket.isConnected) sendJoin() else mutableState.value = ChannelState.Joining
            }
        }
    }

    fun leave() {
        socket.post {
            wanted = false
            rejoinJob?.cancel()
            val ref = joinRef
            joinRef = null
            if (ref != null) socket.send(Frame(ref, socket.nextRef(), topic, Phoenix.LEAVE))
            mutableState.value = ChannelState.Left
            socket.remove(this)
        }
    }

    /** Pushes [event] and waits for the reply (`typing` on a topic channel, for one). */
    suspend fun push(
        event: String,
        payload: JsonObject = Frame.EMPTY,
    ): Reply =
        socket.confined {
            val ref = joinRef
            if (ref == null || mutableState.value !is ChannelState.Joined) {
                Reply.NotJoined
            } else {
                socket.request(Frame(ref, socket.nextRef(), topic, event, payload))
            }
        }

    internal fun socketOpened() {
        if (wanted) sendJoin()
    }

    internal fun socketDropped() {
        joinRef = null
        rejoinJob?.cancel()
        if (wanted) mutableState.value = ChannelState.Errored
    }

    internal fun received(frame: Frame) {
        if (frame.joinRef != null && frame.joinRef != joinRef) return // From an earlier join.
        when (frame.event) {
            Phoenix.REPLY -> {
                Unit
            }

            // Replies are the socket's.
            Phoenix.ERROR -> {
                joinRef = null
                mutableState.value = ChannelState.Errored
                scheduleRejoin()
            }

            Phoenix.CLOSE -> {
                joinRef = null
                wanted = false
                mutableState.value = ChannelState.Closed
            }

            else -> {
                mutableMessages.tryEmit(ChannelMessage.Event(frame.event, frame.payload))
            }
        }
    }

    private fun sendJoin() {
        rejoinJob?.cancel()
        val ref = socket.nextRef()
        joinRef = ref
        mutableState.value = ChannelState.Joining
        socket.scope.launch {
            val reply = socket.request(Frame(ref, ref, topic, Phoenix.JOIN, params()))
            if (joinRef != ref) return@launch
            when (reply) {
                is Reply.Ok -> {
                    attempts = 0
                    mutableMessages.tryEmit(
                        ChannelMessage.Joined(reply.response, rejoined = joinedBefore),
                    )
                    joinedBefore = true
                    mutableState.value = ChannelState.Joined(reply.response)
                }

                is Reply.Error -> {
                    joinRef = null
                    wanted = false
                    mutableState.value = ChannelState.Refused(reply.reason)
                }

                Reply.Timeout -> {
                    joinRef = null
                    mutableState.value = ChannelState.Errored
                    scheduleRejoin()
                }

                // The socket dropped: it joins again when it reopens.
                Reply.Disconnected, Reply.NotJoined -> {
                    Unit
                }
            }
        }
    }

    private fun scheduleRejoin() {
        if (!wanted) return
        val wait = socket.options.backoff(++attempts)
        rejoinJob =
            socket.scope.launch {
                delay(wait)
                if (wanted && socket.isConnected && joinRef == null) sendJoin()
            }
    }
}
