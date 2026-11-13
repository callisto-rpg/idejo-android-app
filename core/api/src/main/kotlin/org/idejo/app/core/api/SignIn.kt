// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.api

import okhttp3.HttpUrl
import org.idejo.app.core.model.OAuthInfo
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** The built-in client (API guide §2): one app, no registration. */
object IdejoClient {
    const val ID = "org.idejo.app"
    const val REDIRECT_URI = "org.idejo.app:/oauth/callback"
    val SCOPES = listOf("read", "write", "private", "moderate", "push")
}

/**
 * One sign-in in progress (SI-03): kept until the redirect comes back, then dropped. The
 * [verifier] never leaves the device but in the token exchange.
 */
data class SignInRequest(
    val address: ForumAddress,
    val oauth: OAuthInfo,
    val verifier: String = Pkce.newVerifier(),
    val state: String = Pkce.newState(),
) {
    /**
     * The page to open in a Custom Tab, asking for every scope the forum offers, or null
     * when discovery's `authorize` leads off the forum (SY-04).
     */
    fun authorizeUrl(deviceName: String): HttpUrl? =
        address
            .resolve(oauth.authorize)
            ?.newBuilder()
            ?.apply {
                addQueryParameter("response_type", "code")
                addQueryParameter("client_id", IdejoClient.ID)
                addQueryParameter("redirect_uri", IdejoClient.REDIRECT_URI)
                addQueryParameter(
                    "scope",
                    oauth.scopes.ifEmpty { IdejoClient.SCOPES }.joinToString(" "),
                )
                addQueryParameter("state", state)
                addQueryParameter("code_challenge", Pkce.challenge(verifier))
                addQueryParameter("code_challenge_method", "S256")
                addQueryParameter("device_name", deviceName)
            }?.build()

    /** What the redirect to `org.idejo.app:/oauth/callback` says (SI-03). */
    fun callback(redirect: String): Callback {
        val uri =
            try {
                URI(redirect)
            } catch (_: java.net.URISyntaxException) {
                return Callback.Invalid
            }
        if ("${uri.scheme}:${uri.path}" != IdejoClient.REDIRECT_URI) return Callback.Invalid
        val query = parseQuery(uri.rawQuery)
        if (query["state"] != state) return Callback.WrongState
        query["error"]?.let {
            return if (it ==
                "access_denied"
            ) {
                Callback.Denied
            } else {
                Callback.Error(it)
            }
        }
        return query["code"]?.takeIf { it.isNotEmpty() }?.let(Callback::Code) ?: Callback.Invalid
    }

    sealed interface Callback {
        data class Code(
            val code: String,
        ) : Callback

        /** Cancel on the forum's page: return quietly to the previous screen. */
        data object Denied : Callback

        data class Error(
            val error: String,
        ) : Callback

        /** Not this sign-in's `state`: ignored. */
        data object WrongState : Callback

        data object Invalid : Callback
    }

    private fun parseQuery(raw: String?): Map<String, String> =
        raw.orEmpty().split('&').filter { it.isNotEmpty() }.associate { pair ->
            val name = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            decode(name) to decode(value)
        }

    private fun decode(text: String): String = URLDecoder.decode(text, StandardCharsets.UTF_8)
}
