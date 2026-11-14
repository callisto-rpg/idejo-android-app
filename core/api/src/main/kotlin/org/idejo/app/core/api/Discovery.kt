// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.idejo.app.core.model.Discovery
import java.io.IOException

/** What `GET /.well-known/idejo` means for adding a forum (SI-02). */
sealed interface DiscoveryResult {
    data class Found(
        val address: ForumAddress,
        val discovery: Discovery,
    ) : DiscoveryResult

    /** No `api`: "This is not an idejo forum, or its version is too old for the app". */
    data object NotIdejo : DiscoveryResult

    /** `api: null`: "The administrators of {name} have turned off the app". */
    data class AppTurnedOff(
        val name: String,
    ) : DiscoveryResult

    /** `api.versions` without `v1`: "This forum needs a newer version of the app". */
    data class NeedsNewerApp(
        val name: String,
    ) : DiscoveryResult

    /** No answer, or a server error: try again later. */
    data class Unreachable(
        val cause: Throwable?,
    ) : DiscoveryResult
}

/** Asks a forum what it is, without signing in (API guide §1). */
class DiscoveryClient(
    private val http: OkHttpClient,
) {
    suspend fun discover(address: ForumAddress): DiscoveryResult =
        withContext(Dispatchers.IO) {
            val url = address.resolve(WELL_KNOWN) ?: return@withContext DiscoveryResult.NotIdejo
            try {
                http
                    .newCall(
                        Request
                            .Builder()
                            .url(url)
                            .header("Accept", "application/json")
                            .build(),
                    ).execute()
                    .use {
                        when {
                            it.isSuccessful -> read(address, it.body.string())
                            it.code in CLIENT_ERRORS -> DiscoveryResult.NotIdejo
                            else -> DiscoveryResult.Unreachable(null)
                        }
                    }
            } catch (e: IOException) {
                DiscoveryResult.Unreachable(e)
            }
        }

    /** Tells a missing `api` from a null one, then reads the rest leniently (SY-07). */
    internal fun read(
        address: ForumAddress,
        body: String,
    ): DiscoveryResult {
        val json =
            try {
                IdejoJson.parseToJsonElement(body) as? JsonObject
            } catch (_: SerializationException) {
                null
            } ?: return DiscoveryResult.NotIdejo
        val api = json["api"] ?: return DiscoveryResult.NotIdejo
        val discovery =
            try {
                IdejoJson.decodeFromJsonElement(Discovery.serializer(), json)
            } catch (_: SerializationException) {
                return DiscoveryResult.NotIdejo
            } catch (_: IllegalArgumentException) {
                return DiscoveryResult.NotIdejo
            }
        return when {
            api is JsonNull -> DiscoveryResult.AppTurnedOff(discovery.name)

            discovery.api?.versions?.contains(
                API_VERSION,
            ) != true -> DiscoveryResult.NeedsNewerApp(discovery.name)

            else -> DiscoveryResult.Found(address, discovery)
        }
    }

    private companion object {
        const val WELL_KNOWN = "/.well-known/idejo"
        const val API_VERSION = "v1"
        val CLIENT_ERRORS = 400..499
    }
}
