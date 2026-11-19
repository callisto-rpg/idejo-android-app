// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.idejo.app.core.model.DirectoryPage
import java.io.IOException

/**
 * The group's directory of forums (SI-08), at discovery's `community.directory_url`: public,
 * read without a token, 20 forums a page, searched by name or address. It answers 120
 * requests a minute; past that, `429 rate_limited` with `Retry-After`.
 */
class DirectoryClient(
    private val http: OkHttpClient,
) {
    /** The forums matching [query] (all when blank), [page] from 1. */
    suspend fun page(
        directoryUrl: HttpUrl,
        query: String,
        page: Int,
    ): ApiResult<DirectoryPage> =
        withContext(Dispatchers.IO) {
            val url =
                directoryUrl
                    .newBuilder()
                    .addPathSegments("api/v1/forums")
                    .addQueryParameter("q", query.trim())
                    .addQueryParameter("page", page.toString())
                    .build()
            try {
                http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext ApiResult.Failure(
                            problemOf(response),
                        )
                    }
                    try {
                        ApiResult.Success(
                            IdejoJson.decodeFromString<DirectoryPage>(response.body.string()),
                        )
                    } catch (e: SerializationException) {
                        ApiResult.Failure(ApiProblem.Malformed(response.code, e))
                    } catch (e: IllegalArgumentException) {
                        ApiResult.Failure(ApiProblem.Malformed(response.code, e))
                    }
                }
            } catch (e: IOException) {
                ApiResult.Failure(ApiProblem.Network(e))
            }
        }
}
