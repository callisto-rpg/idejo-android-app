// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.testing

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.idejo.app.core.api.IdejoJson

/** One app connection to the fake's socket: Phoenix Channels 2.0.0, as idejo's ApiSocket. */
@Suppress("TooManyFunctions") // The protocol's replies, each small.
internal class FakeSocket(
    private val fake: FakeIdejo,
    val token: String,
) {
    @Volatile private var socket: WebSocket? = null

    /** Joined topics and their join refs. */
    private val joined = java.util.concurrent.ConcurrentHashMap<String, String>()

    val listener =
        object : WebSocketListener() {
            override fun onOpen(
                webSocket: WebSocket,
                response: Response,
            ) {
                socket = webSocket
            }

            override fun onMessage(
                webSocket: WebSocket,
                text: String,
            ) = received(text)

            override fun onClosing(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                webSocket.close(code, null)
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                fake.forget(this@FakeSocket)
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?,
            ) {
                fake.forget(this@FakeSocket)
            }
        }

    fun isJoined(topic: String): Boolean = joined.containsKey(topic)

    fun push(
        topic: String,
        event: String,
        payload: JsonElement,
    ) {
        val joinRef = joined[topic] ?: return
        send(joinRef, null, topic, event, payload)
    }

    /** `token.revoked` on user:me, then the socket closes (API guide §3). */
    fun revoked() {
        push("user:me", "token.revoked", buildJsonObject { put("token_id", null as String?) })
        close()
    }

    fun close() {
        socket?.close(NORMAL_CLOSURE, "closed")
    }

    private fun received(text: String) {
        val frame = IdejoJson.parseToJsonElement(text) as? JsonArray ?: return
        if (frame.size != FRAME_SIZE) return
        val head = frame.map { it.string() }
        val joinRef = head[JOIN_REF]
        val ref = head[REF]
        val topic = head[TOPIC] ?: return
        when (head[EVENT]) {
            "heartbeat" -> {
                reply(joinRef, ref, topic, ok())
            }

            "phx_join" -> {
                join(requireNotNull(ref), topic)
            }

            "phx_leave" -> {
                joined.remove(topic)
                reply(joinRef, ref, topic, ok())
            }

            // idejo keeps the time on its own clock; typing is never broadcast.
            "typing" -> {
                reply(
                    joinRef,
                    ref,
                    topic,
                    if (joined[topic] ==
                        joinRef
                    ) {
                        ok()
                    } else {
                        error("not_joined")
                    },
                )
            }

            else -> {
                reply(joinRef, ref, topic, error("unknown_event"))
            }
        }
    }

    private fun join(
        ref: String,
        topic: String,
    ) {
        val response =
            when {
                topic == "user:me" -> {
                    buildJsonObject {
                        put(
                            "unread",
                            buildJsonObject { put("count", 0) },
                        )
                    }
                }

                topic.startsWith("topic:") -> {
                    fake.joinTopic(topic.removePrefix("topic:"))
                }

                topic.startsWith("conversation:") -> {
                    fake.joinConversation(topic.removePrefix("conversation:"), token)
                }

                topic.startsWith("category:") -> {
                    fake.joinCategory(topic.removePrefix("category:"))
                }

                else -> {
                    null
                }
            }
        if (response == null) {
            reply(ref, ref, topic, error("not_found"))
        } else {
            joined[topic] = ref
            reply(ref, ref, topic, ok(response))
        }
    }

    private fun reply(
        joinRef: String?,
        ref: String?,
        topic: String,
        payload: JsonObject,
    ) = send(joinRef, ref, topic, "phx_reply", payload)

    private fun send(
        joinRef: String?,
        ref: String?,
        topic: String,
        event: String,
        payload: JsonElement,
    ) {
        val frame =
            JsonArray(
                listOf(
                    joinRef.json(),
                    ref.json(),
                    JsonPrimitive(topic),
                    JsonPrimitive(event),
                    payload,
                ),
            )
        socket?.send(frame.toString())
    }

    private fun ok(response: JsonObject = JsonObject(emptyMap())) =
        JsonObject(mapOf("status" to JsonPrimitive("ok"), "response" to response))

    private fun error(reason: String) =
        JsonObject(
            mapOf(
                "status" to JsonPrimitive("error"),
                "response" to buildJsonObject { put("reason", reason) },
            ),
        )

    private fun JsonElement.string(): String? =
        (this as? JsonPrimitive)
            ?.takeUnless {
                it is JsonNull
            }?.contentOrNull

    private fun String?.json(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull

    private companion object {
        const val NORMAL_CLOSURE = 1000

        // `[join_ref, ref, topic, event, payload]`.
        const val JOIN_REF = 0
        const val REF = 1
        const val TOPIC = 2
        const val EVENT = 3
        const val FRAME_SIZE = 5
    }
}
