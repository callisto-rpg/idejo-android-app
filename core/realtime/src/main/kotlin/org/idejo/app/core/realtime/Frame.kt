// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.realtime

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.idejo.app.core.api.IdejoJson

/**
 * One message of Phoenix Channels protocol 2.0.0: `[join_ref, ref, topic, event, payload]`.
 */
data class Frame(
    val joinRef: String?,
    val ref: String?,
    val topic: String,
    val event: String,
    val payload: JsonElement = EMPTY,
) {
    fun encode(): String =
        JsonArray(
            listOf(
                joinRef?.let(::JsonPrimitive) ?: JsonNull,
                ref?.let(::JsonPrimitive) ?: JsonNull,
                JsonPrimitive(topic),
                JsonPrimitive(event),
                payload,
            ),
        ).toString()

    companion object {
        val EMPTY = JsonObject(emptyMap())

        /** A frame, or null for anything else (SY-07: ignored). */
        fun decode(text: String): Frame? {
            val array =
                try {
                    IdejoJson.parseToJsonElement(text) as? JsonArray
                } catch (_: SerializationException) {
                    null
                }
            if (array == null || array.size != FIELDS) return null
            val topic = (array[TOPIC] as? JsonPrimitive)?.contentOrNull
            val event = (array[EVENT] as? JsonPrimitive)?.contentOrNull
            if (topic == null || event == null) return null
            return Frame(
                array[JOIN_REF].stringOrNull(),
                array[REF].stringOrNull(),
                topic,
                event,
                array[PAYLOAD],
            )
        }

        private fun JsonElement.stringOrNull(): String? =
            (this as? JsonPrimitive)
                ?.takeUnless {
                    it is JsonNull
                }?.content

        private const val JOIN_REF = 0
        private const val REF = 1
        private const val TOPIC = 2
        private const val EVENT = 3
        private const val PAYLOAD = 4
        private const val FIELDS = 5
    }
}

/** The protocol's own events. */
internal object Phoenix {
    const val TOPIC = "phoenix"
    const val HEARTBEAT = "heartbeat"
    const val JOIN = "phx_join"
    const val LEAVE = "phx_leave"
    const val REPLY = "phx_reply"
    const val ERROR = "phx_error"
    const val CLOSE = "phx_close"
}

/** The answer to a push, a join or a heartbeat. */
sealed interface Reply {
    data class Ok(
        val response: JsonElement,
    ) : Reply

    data class Error(
        val response: JsonElement,
    ) : Reply {
        /** `{"reason": "not_found"}` and the like. */
        val reason: String?
            get() = ((response as? JsonObject)?.get("reason") as? JsonPrimitive)?.contentOrNull
    }

    /** No answer in time. */
    data object Timeout : Reply

    /** The socket closed first, or was not open. */
    data object Disconnected : Reply

    /** A push on a channel that is not joined. */
    data object NotJoined : Reply

    companion object {
        internal fun of(payload: JsonElement): Reply {
            val body = payload as? JsonObject
            val status = (body?.get("status") as? JsonPrimitive)?.contentOrNull
            val response = body?.get("response") ?: Frame.EMPTY
            return if (status == "ok") Ok(response) else Error(response)
        }
    }
}
