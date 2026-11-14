// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.api

import kotlinx.serialization.SerializationException
import okhttp3.Response
import org.idejo.app.core.model.ApiError
import org.idejo.app.core.model.ErrorBody
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** What an API call gave: the value, or why not. */
sealed interface ApiResult<out T> {
    data class Success<T>(
        val value: T,
    ) : ApiResult<T>

    data class Failure(
        val problem: ApiProblem,
    ) : ApiResult<Nothing>
}

fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.value

fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> =
    when (this) {
        is ApiResult.Success -> ApiResult.Success(transform(value))
        is ApiResult.Failure -> this
    }

/** Why a call failed. */
sealed interface ApiProblem {
    /** The server answered with an error (API guide §4). */
    data class Server(
        val status: Int,
        val error: ApiError,
        /** From `Retry-After` (429), when given. */
        val retryAfter: Duration? = null,
    ) : ApiProblem {
        /** `401 invalid_token`: the device was signed out (SI-09, SI-12). */
        val signedOut: Boolean get() =
            status == HTTP_UNAUTHORIZED &&
                error.code == ApiError.INVALID_TOKEN

        /** `403 banned` or `403 suspended` (SI-10): writing is disabled. */
        val barred: Boolean
            get() =
                status == HTTP_FORBIDDEN &&
                    error.code in setOf(ApiError.BANNED, ApiError.SUSPENDED)
    }

    /** No answer: offline, timed out, refused. */
    data class Network(
        val cause: IOException,
    ) : ApiProblem

    /** An answer that could not be read. */
    data class Malformed(
        val status: Int,
        val cause: Throwable? = null,
    ) : ApiProblem
}

internal const val HTTP_UNAUTHORIZED = 401
internal const val HTTP_FORBIDDEN = 403
internal const val HTTP_NOT_FOUND = 404
internal const val HTTP_TOO_MANY_REQUESTS = 429

/** The error an unsuccessful [response] carries; its body is read and closed. */
internal fun problemOf(response: Response): ApiProblem =
    errorProblem(
        response.code,
        response.body.string(),
        RetryAfter.parse(response.header("Retry-After"), Instant.now()),
    )

internal fun errorProblem(
    status: Int,
    body: String,
    retryAfter: Duration?,
): ApiProblem =
    try {
        ApiProblem.Server(status, IdejoJson.decodeFromString<ErrorBody>(body).error, retryAfter)
    } catch (e: SerializationException) {
        // A proxy's page, or an error without the API's shape.
        if (status == HTTP_TOO_MANY_REQUESTS) {
            ApiProblem.Server(status, ApiError(ApiError.RATE_LIMITED), retryAfter)
        } else {
            ApiProblem.Malformed(status, e)
        }
    } catch (e: IllegalArgumentException) {
        ApiProblem.Malformed(status, e)
    }

/** `Retry-After`: seconds, or an HTTP date (RFC 9110 §10.2.3). */
object RetryAfter {
    fun parse(
        header: String?,
        now: Instant,
    ): Duration? {
        val value = header?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        value.toLongOrNull()?.let { return Duration.ofSeconds(it.coerceAtLeast(0)) }
        return try {
            val at = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            Duration.between(now, at).takeUnless { it.isNegative } ?: Duration.ZERO
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
