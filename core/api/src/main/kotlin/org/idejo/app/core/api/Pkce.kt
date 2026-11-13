// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.api

import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import java.security.SecureRandom

/** PKCE (RFC 7636) with S256, and the random `state` (SI-03). */
object Pkce {
    private val random = SecureRandom()

    /** 64 characters of `A-Z a-z 0-9 - _` (from 48 random bytes). */
    fun newVerifier(): String = randomUrlSafe(VERIFIER_BYTES)

    /** BASE64URL(SHA-256(verifier)), unpadded. */
    fun challenge(verifier: String): String =
        verifier
            .encodeUtf8()
            .sha256()
            .base64Url()
            .trimEnd('=')

    fun newState(): String = randomUrlSafe(STATE_BYTES)

    private fun randomUrlSafe(bytes: Int): String =
        ByteArray(bytes)
            .also(random::nextBytes)
            .toByteString()
            .base64Url()
            .trimEnd('=')

    private const val VERIFIER_BYTES = 48
    private const val STATE_BYTES = 24
}
