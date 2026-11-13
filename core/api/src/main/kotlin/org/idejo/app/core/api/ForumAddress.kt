// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.api

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A forum's address: its https origin, nothing else (SI-01). Every URL the app sends a
 * token or a code to is resolved against it and must stay on it (SY-04).
 */
class ForumAddress private constructor(
    /** `https://host[:port]/`. */
    val origin: HttpUrl,
) {
    val host: String get() = origin.host

    /**
     * [path] (from discovery, or a URL the server gave) against the forum's address, or
     * null if it leads to another host, scheme or port.
     */
    fun resolve(path: String): HttpUrl? = origin.resolve(path)?.takeIf { isSameOrigin(it) }

    fun isSameOrigin(url: HttpUrl): Boolean =
        url.scheme == origin.scheme && url.host == origin.host && url.port == origin.port

    override fun equals(other: Any?): Boolean = other is ForumAddress && other.origin == origin

    override fun hashCode(): Int = origin.hashCode()

    override fun toString(): String = origin.toString()

    /** Why a typed address was refused. */
    enum class Problem {
        Empty,

        /** `http://`, or another scheme: https is required (SY-01). */
        NotHttps,
        Malformed,
    }

    sealed interface Parsed {
        data class Valid(
            val address: ForumAddress,
        ) : Parsed

        data class Invalid(
            val problem: Problem,
        ) : Parsed
    }

    companion object {
        /** What the member typed: scheme optional, https assumed and required (SI-01). */
        fun parse(typed: String): Parsed {
            val text = typed.trim()
            if (text.isEmpty()) return Parsed.Invalid(Problem.Empty)
            val scheme =
                SCHEME
                    .find(text)
                    ?.groupValues
                    ?.get(1)
                    ?.lowercase()
            if (scheme != null && scheme != "https") return Parsed.Invalid(Problem.NotHttps)
            val url = (if (scheme == null) "https://$text" else text).toHttpUrlOrNull()
            // A forum has a domain name; "localhost" only for a development server.
            val named = url != null && (url.host.contains('.') || url.host == "localhost")
            if (url == null || !named) return Parsed.Invalid(Problem.Malformed)
            return Parsed.Valid(
                ForumAddress(
                    url
                        .newBuilder()
                        .encodedPath("/")
                        .query(null)
                        .fragment(null)
                        .build(),
                ),
            )
        }

        /** A forum at [url] as it is, for tests against a local server only. */
        internal fun forTesting(url: HttpUrl): ForumAddress =
            ForumAddress(
                url
                    .newBuilder()
                    .encodedPath("/")
                    .query(null)
                    .fragment(null)
                    .build(),
            )

        private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://")
    }
}
