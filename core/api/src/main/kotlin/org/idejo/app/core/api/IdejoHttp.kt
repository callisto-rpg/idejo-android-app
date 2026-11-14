// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.api

import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The app's one HTTP client, shared by every forum for its connection pool; each forum
 * derives its own with its token (ForumApi). https only (SY-01): cleartext is refused here
 * as well as by the network security config, and https never redirects to http. No
 * logging interceptor, so no token or body reaches a log (SI-04).
 */
object IdejoHttp {
    fun newClient(userAgent: String): OkHttpClient =
        builder(userAgent, allowCleartext = false).build()

    internal fun builder(
        userAgent: String,
        allowCleartext: Boolean,
    ): OkHttpClient.Builder =
        OkHttpClient
            .Builder()
            .connectionSpecs(
                if (allowCleartext) {
                    listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.CLEARTEXT)
                } else {
                    listOf(ConnectionSpec.MODERN_TLS)
                },
            ).followSslRedirects(false)
            .connectTimeout(CONNECT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(
                Interceptor { chain ->
                    chain.proceed(
                        chain
                            .request()
                            .newBuilder()
                            .header("User-Agent", userAgent)
                            .build(),
                    )
                },
            )

    /** A client for tests and the fake server, which speak http. Never in the app. */
    fun forLocalTesting(): OkHttpClient = builder("idejo-test", allowCleartext = true).build()

    private const val CONNECT_SECONDS = 15L
    private const val READ_SECONDS = 30L
}
