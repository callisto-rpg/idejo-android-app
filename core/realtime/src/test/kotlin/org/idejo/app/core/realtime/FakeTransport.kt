// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.realtime

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.idejo.app.core.api.IdejoJson

/** Stands in for the network: records what the socket sends, plays the server. */
internal class FakeTransport : SocketTransport {
    val connections = mutableListOf<FakeConnection>()
    var hasToken = true

    val last: FakeConnection get() = connections.last()

    override fun connect(listener: TransportListener): TransportConnection? =
        if (hasToken) FakeConnection(listener).also { connections += it } else null
}

internal class FakeConnection(
    val listener: TransportListener,
) : TransportConnection {
    val sent = mutableListOf<Frame>()
    var closed = false

    override fun send(text: String): Boolean {
        if (closed) return false
        sent += requireNotNull(Frame.decode(text))
        return true
    }

    override fun close(
        code: Int,
        reason: String,
    ) {
        closed = true
    }

    fun open() = listener.onOpen()

    fun drop() = listener.onClosed(1006)

    fun sent(event: String): List<Frame> = sent.filter { it.event == event }

    fun reply(
        to: Frame,
        status: String = "ok",
        response: String = "{}",
    ) {
        val payload =
            JsonObject(
                mapOf(
                    "status" to JsonPrimitive(status),
                    "response" to IdejoJson.parseToJsonElement(response),
                ),
            )
        listener.onMessage(Frame(to.joinRef, to.ref, to.topic, Phoenix.REPLY, payload).encode())
    }

    fun push(
        topic: String,
        event: String,
        payload: String,
        joinRef: String? = sent(Phoenix.JOIN).lastOrNull { it.topic == topic }?.ref,
    ) = listener.onMessage(
        Frame(joinRef, null, topic, event, IdejoJson.parseToJsonElement(payload)).encode(),
    )
}

internal fun json(text: String): JsonElement = IdejoJson.parseToJsonElement(text)
