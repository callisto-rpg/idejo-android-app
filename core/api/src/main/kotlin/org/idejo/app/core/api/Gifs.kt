// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.idejo.app.core.model.GifConfig
import java.io.IOException

/** A GIF of the provider: [preview] in the picker, [full] in the message (CO-15). */
data class Gif(
    val id: String,
    val slug: String,
    val title: String,
    val preview: String,
    val full: String,
)

/** A page of GIFs, and whether there is another. */
data class GifPage(
    val items: List<Gif>,
    val more: Boolean,
)

/**
 * The GIF provider the forum names (`GET /gifs`, CO-15), called by the app itself as the
 * web's browsers do: never through the forum, never with the member's token, no
 * identifier of the member. Results keep the provider's order; ads are left out.
 */
interface GifSearch {
    /** The popular GIFs without [query], else the search's; [page] from 1. */
    suspend fun page(
        config: GifConfig,
        query: String?,
        page: Int,
    ): GifPage?

    /** The provider's share trigger, with the search that found [gif]; failures ignored. */
    suspend fun shared(
        config: GifConfig,
        gif: Gif,
        query: String?,
    )

    companion object {
        /** The adapter of [GifConfig.provider], or null for one the app does not know. */
        fun of(
            http: OkHttpClient,
            provider: String?,
        ): GifSearch? = if (provider == Klipy.ID) Klipy(http) else null
    }
}

/** KLIPY (api.klipy.com), on its standard terms. */
class Klipy(
    private val http: OkHttpClient,
    private val base: String = API,
) : GifSearch {
    override suspend fun page(
        config: GifConfig,
        query: String?,
        page: Int,
    ): GifPage? =
        withContext(Dispatchers.IO) {
            val url =
                "$base/${config.key}/gifs/${if (query.isNullOrBlank()) "trending" else "search"}"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("page", page.toString())
                    .addQueryParameter("per_page", PER_PAGE.toString())
                    .apply {
                        if (!query.isNullOrBlank()) {
                            addQueryParameter("q", query)
                            addQueryParameter("content_filter", config.filter ?: "medium")
                        }
                    }.build()
            try {
                http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    parse(response.body.string())
                }
            } catch (_: IOException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

    override suspend fun shared(
        config: GifConfig,
        gif: Gif,
        query: String?,
    ) {
        withContext(Dispatchers.IO) {
            val body = (
                if (query.isNullOrBlank()) {
                    "{}"
                } else {
                    IdejoJson.encodeToString(
                        mapOf("q" to query),
                    )
                }
            )
            val request =
                Request
                    .Builder()
                    .url("$base/${config.key}/gifs/share/${gif.slug}")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
            try {
                http.newCall(request).execute().close()
            } catch (_: IOException) {
                // The share trigger only helps the provider rank; nothing to do.
            } catch (_: IllegalArgumentException) {
                // A slug that is not a path: nothing to share.
            }
        }
    }

    private fun parse(text: String): GifPage {
        val data = IdejoJson.parseToJsonElement(text).jsonObject["data"] as? JsonObject
        val list = (data?.get("data") as? JsonArray).orEmpty()
        val items =
            list.mapNotNull { element ->
                val gif = element as? JsonObject ?: return@mapNotNull null
                if (gif["type"]?.jsonPrimitive?.contentOrNull == "ad") return@mapNotNull null
                val files = gif["file"] as? JsonObject ?: return@mapNotNull null
                val preview = file(files, PREVIEW) ?: return@mapNotNull null
                val full = file(files, FULL) ?: return@mapNotNull null
                Gif(
                    gif["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                    gif["slug"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    gif["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    preview,
                    full,
                )
            }
        return GifPage(items, data?.get("has_next")?.jsonPrimitive?.booleanOrNull == true)
    }

    /** The size wanted, else another; WebP, else the GIF itself. */
    private fun file(
        files: JsonObject,
        sizes: List<String>,
    ): String? =
        sizes.firstNotNullOfOrNull { size ->
            val formats = files[size] as? JsonObject ?: return@firstNotNullOfOrNull null
            listOf("webp", "gif").firstNotNullOfOrNull { format ->
                (formats[format] as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull
            }
        }

    companion object {
        const val ID = "klipy"
        private const val API = "https://api.klipy.com/api/v1"
        private const val PER_PAGE = 24
        private val PREVIEW = listOf("sm", "xs", "md", "hd")
        private val FULL = listOf("md", "hd", "sm", "xs")
    }
}

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
