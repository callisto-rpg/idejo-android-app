// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.realtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Where the socket stands. */
sealed interface SocketState {
    data object Disconnected : SocketState

    data object Connecting : SocketState

    data object Connected : SocketState

    /** Dropped; trying again after [delay] ([attempt] since the last success). */
    data class Waiting(
        val attempt: Int,
        val delay: Duration,
    ) : SocketState

    /** The server refused: not tried again until [PhoenixSocket.connect] is called anew. */
    data class Refused(
        val reason: Refusal,
    ) : SocketState
}

enum class Refusal {
    /** 403: the token is no longer valid (SI-09). */
    SignedOut,

    /** 404: the administrators turned the API off. */
    ApiOff,

    /** No token to connect with (a visitor, SI-05). */
    NoToken,
}

data class SocketOptions(
    val heartbeat: Duration = 30.seconds,
    val timeout: Duration = 10.seconds,
    /** The wait before reconnect (or rejoin) number `attempt`, from 1. */
    val backoff: (attempt: Int) -> Duration = ::defaultBackoff,
)

/** 1, 2, 5, 10, then 30 seconds between tries. */
fun defaultBackoff(attempt: Int): Duration =
    BACKOFF_SECONDS.getOrElse(attempt - 1) { BACKOFF_SECONDS.last() }.seconds

private val BACKOFF_SECONDS = listOf(1, 2, 5, 10, 30)

/**
 * A Phoenix Channels v2 socket (API guide §7): heartbeat, channels joined again after a
 * reconnect, reconnects with backoff, never after a refusal. All its state lives on
 * [scope], whose dispatcher must run one coroutine at a time (as
 * `Dispatchers.Default.limitedParallelism(1)` does); transport callbacks are moved there.
 * No polling, and nothing runs while disconnected (NF-04).
 */
@Suppress("TooManyFunctions") // The protocol's steps, each small, kept private.
class PhoenixSocket(
    private val transport: SocketTransport,
    internal val scope: CoroutineScope,
    internal val options: SocketOptions = SocketOptions(),
) {
    private val confined: CoroutineContext =
        requireNotNull(scope.coroutineContext[ContinuationInterceptor])
    private val mutableState = MutableStateFlow<SocketState>(SocketState.Disconnected)
    val state: StateFlow<SocketState> = mutableState.asStateFlow()

    private var connection: TransportConnection? = null
    private var generation = 0
    private var wanted = false
    private var attempts = 0
    private var lastRef = 0L
    private var pendingHeartbeat: String? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private val replies = mutableMapOf<String, CompletableDeferred<Reply>>()
    private val channels = mutableListOf<PhoenixChannel>()

    internal val isConnected: Boolean get() = mutableState.value == SocketState.Connected

    /** Connects, and keeps connecting after drops until [disconnect]. */
    fun connect() {
        post {
            if (!wanted || mutableState.value is SocketState.Refused) {
                wanted = true
                attempts = 0
                if (connection == null) open()
            }
        }
    }

    /** Closes the socket; channels stay registered and are joined again on [connect]. */
    fun disconnect() {
        post {
            wanted = false
            reconnectJob?.cancel()
            val open = connection
            dropped()
            open?.close(NORMAL_CLOSURE, "bye")
            mutableState.value = SocketState.Disconnected
        }
    }

    /** A channel on [topic]; [params] are read at every join ([PhoenixChannel.join]). */
    fun channel(
        topic: String,
        params: () -> JsonObject = { Frame.EMPTY },
    ): PhoenixChannel =
        PhoenixChannel(this, topic, params).also { channel ->
            post {
                channels +=
                    channel
            }
        }

    internal fun post(block: () -> Unit) {
        scope.launch { block() }
    }

    internal suspend fun <T> confined(block: suspend () -> T): T = withContext(confined) { block() }

    internal fun nextRef(): String = (++lastRef).toString()

    internal fun remove(channel: PhoenixChannel) {
        channels -= channel
    }

    internal fun send(frame: Frame): Boolean = connection?.send(frame.encode()) ?: false

    /** Sends [frame] and waits for its reply. */
    internal suspend fun request(frame: Frame): Reply {
        val ref = requireNotNull(frame.ref)
        val reply = CompletableDeferred<Reply>()
        replies[ref] = reply
        if (!send(frame)) {
            replies.remove(ref)
            return Reply.Disconnected
        }
        return withTimeoutOrNull(options.timeout) { reply.await() }
            ?: Reply.Timeout.also { replies.remove(ref) }
    }

    private fun open() {
        val current = ++generation
        mutableState.value = SocketState.Connecting
        connection = transport.connect(Listener(current))
        if (connection == null) refuse(Refusal.NoToken)
    }

    private inner class Listener(
        private val generation: Int,
    ) : TransportListener {
        private fun current(block: () -> Unit) =
            post {
                if (generation ==
                    this@PhoenixSocket.generation
                ) {
                    block()
                }
            }

        override fun onOpen() = current(::opened)

        override fun onMessage(text: String) = current { received(text) }

        override fun onClosed(code: Int) = current(::lost)

        override fun onFailure(
            cause: Throwable,
            status: Int?,
        ) = current {
            when (status) {
                HTTP_FORBIDDEN -> refuse(Refusal.SignedOut)
                HTTP_NOT_FOUND -> refuse(Refusal.ApiOff)
                else -> lost()
            }
        }
    }

    private fun opened() {
        mutableState.value = SocketState.Connected
        attempts = 0
        heartbeatJob = scope.launch { beat() }
        channels.toList().forEach { it.socketOpened() }
    }

    private suspend fun beat() {
        while (true) {
            delay(options.heartbeat)
            if (pendingHeartbeat != null) {
                // The last one was never answered: the connection is dead.
                val open = connection
                lost()
                open?.close(NORMAL_CLOSURE, "heartbeat timeout")
                return
            }
            val ref = nextRef()
            pendingHeartbeat = ref
            send(Frame(null, ref, Phoenix.TOPIC, Phoenix.HEARTBEAT))
        }
    }

    private fun received(text: String) {
        val frame = Frame.decode(text) ?: return
        if (frame.event == Phoenix.REPLY) {
            if (frame.topic == Phoenix.TOPIC &&
                frame.ref == pendingHeartbeat
            ) {
                pendingHeartbeat = null
            }
            frame.ref?.let { replies.remove(it)?.complete(Reply.of(frame.payload)) }
            if (frame.topic == Phoenix.TOPIC) return
        }
        channels.filter { it.topic == frame.topic }.forEach { it.received(frame) }
    }

    /** The connection is gone: stop everything tied to it. */
    private fun dropped() {
        generation++
        connection = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        pendingHeartbeat = null
        replies.values.forEach { it.complete(Reply.Disconnected) }
        replies.clear()
        channels.toList().forEach { it.socketDropped() }
    }

    private fun lost() {
        dropped()
        if (!wanted) {
            mutableState.value = SocketState.Disconnected
            return
        }
        val delay = options.backoff(++attempts)
        mutableState.value = SocketState.Waiting(attempts, delay)
        reconnectJob =
            scope.launch {
                delay(delay)
                if (wanted && connection == null) open()
            }
    }

    private fun refuse(reason: Refusal) {
        dropped()
        wanted = false
        mutableState.value = SocketState.Refused(reason)
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
    }
}
