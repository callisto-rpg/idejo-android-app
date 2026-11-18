// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The answer of `POST /oauth/token` (API guide §2). No expiry, no refresh token. The
 * token is kept as an [AccessToken], which never prints itself.
 */
@Serializable
data class TokenGrant(
    @SerialName("access_token") val accessToken: AccessToken,
    @SerialName("token_type") val tokenType: String = "Bearer",
    val scope: String = "",
    @SerialName("token_id") val tokenId: String? = null,
    val user: Person? = null,
) {
    val scopes: Set<String> get() = scope.split(' ').filter { it.isNotEmpty() }.toSet()
}

/**
 * A device signed in to the app (`GET /me/tokens`, AP-09), as **Apps and devices** lists it
 * (ST-05). [current] marks the token that asks.
 */
@Serializable
data class Token(
    val id: String,
    val name: String,
    /** What the token may do: `read`, `write`, `private`, `moderate`, `push`. */
    val scopes: List<String> = emptyList(),
    @SerialName("signed_in_at") val signedInAt: Time? = null,
    @SerialName("last_used_at") val lastUsedAt: Time? = null,
    val current: Boolean = false,
)

/** An `idj_…` token (SI-04). Its [toString] hides it, so it cannot reach a log by accident. */
@Serializable
@JvmInline
value class AccessToken(
    val value: String,
) {
    override fun toString(): String = "AccessToken(…)"
}
