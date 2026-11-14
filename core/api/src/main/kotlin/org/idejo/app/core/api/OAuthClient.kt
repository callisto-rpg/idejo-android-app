// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.idejo.app.core.model.AccessToken
import org.idejo.app.core.model.ApiError
import org.idejo.app.core.model.TokenGrant
import java.io.IOException

/** The token exchange and revocation (API guide §2, §3). */
class OAuthClient(
    private val http: OkHttpClient,
) {
    /** Exchanges the code within its 60 seconds (SI-04). */
    suspend fun exchange(
        request: SignInRequest,
        code: String,
    ): ApiResult<TokenGrant> {
        val url =
            request.address.resolve(request.oauth.token)
                ?: return ApiResult.Failure(offForum())
        val form =
            FormBody
                .Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", IdejoClient.REDIRECT_URI)
                .add("client_id", IdejoClient.ID)
                .add("code_verifier", request.verifier)
                .build()
        return withContext(Dispatchers.IO) {
            try {
                http
                    .newCall(
                        Request
                            .Builder()
                            .url(url)
                            .post(form)
                            .build(),
                    ).execute()
                    .use { response ->
                        if (!response.isSuccessful) {
                            return@use ApiResult.Failure(
                                problemOf(response),
                            )
                        }
                        try {
                            ApiResult.Success(
                                IdejoJson.decodeFromString<TokenGrant>(response.body.string()),
                            )
                        } catch (e: SerializationException) {
                            ApiResult.Failure(ApiProblem.Malformed(response.code, e))
                        }
                    }
            } catch (e: IOException) {
                ApiResult.Failure(ApiProblem.Network(e))
            }
        }
    }

    /**
     * Signs the device out (SI-11): always 204 on the server, so this only reports whether
     * the server was reached. The token is sent as Bearer, to its own forum only.
     */
    suspend fun revoke(
        address: ForumAddress,
        revokePath: String,
        token: AccessToken,
    ): Boolean {
        val url = address.resolve(revokePath) ?: return false
        return withContext(Dispatchers.IO) {
            try {
                http
                    .newCall(
                        Request
                            .Builder()
                            .url(url)
                            .header("Authorization", "Bearer ${token.value}")
                            .post(FormBody.Builder().build())
                            .build(),
                    ).execute()
                    .use { it.isSuccessful }
            } catch (_: IOException) {
                false
            }
        }
    }

    private fun offForum() =
        ApiProblem.Server(0, ApiError("invalid_request", "The forum's sign-in leads elsewhere."))
}
