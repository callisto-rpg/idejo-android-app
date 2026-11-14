// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.format.DateTimeParseException

/** The API's one error shape: `{"error": {code, message, details}}` (API guide §4). */
@Serializable
data class ErrorBody(
    val error: ApiError,
)

/**
 * An error the server gave. [code] is stable; [message] is the web's own text, translated,
 * ready to show (SI-10).
 */
@Serializable
data class ApiError(
    val code: String,
    val message: String = "",
    val details: JsonObject = JsonObject(emptyMap()),
) {
    /** `details.until`: when a cooldown or a suspension ends. */
    val until: Instant?
        get() =
            (details["until"] as? JsonPrimitive)?.contentOrNull?.let {
                try {
                    Instant.parse(it)
                } catch (_: DateTimeParseException) {
                    null
                }
            }

    /** `details.fields`: a form's problems (422), each field with its messages. */
    val fields: Map<String, List<String>>
        get() {
            val fields = details["fields"] as? JsonObject ?: return emptyMap()
            return fields.mapValues { (_, value) ->
                when (value) {
                    is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    is JsonPrimitive -> listOfNotNull(value.contentOrNull)
                    else -> emptyList()
                }
            }
        }

    companion object {
        const val UNAUTHENTICATED = "unauthenticated"
        const val INVALID_TOKEN = "invalid_token"
        const val INSUFFICIENT_SCOPE = "insufficient_scope"
        const val BANNED = "banned"
        const val SUSPENDED = "suspended"
        const val FORBIDDEN = "forbidden"
        const val NOT_FOUND = "not_found"
        const val TOO_SOON = "too_soon"
        const val CURSOR_EXPIRED = "cursor_expired"
        const val TOO_LARGE = "too_large"
        const val VALIDATION = "validation"
        const val RATE_LIMITED = "rate_limited"
        const val INVALID_GRANT = "invalid_grant"
    }
}
