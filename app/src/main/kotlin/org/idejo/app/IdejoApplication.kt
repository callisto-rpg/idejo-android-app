// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.idejo.app.core.data.AppScope
import org.idejo.app.core.data.ForegroundSockets
import org.idejo.app.core.data.SessionManager
import javax.inject.Inject

/** The application, Hilt's root component: opens the forum the app had open (MD-05). */
@HiltAndroidApp
class IdejoApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject lateinit var sessions: SessionManager

    @Inject lateinit var foreground: ForegroundSockets

    @Inject lateinit var http: OkHttpClient

    @Inject @AppScope
    lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        foreground.install()
        scope.launch { sessions.restore() }
    }

    /** Pictures through the app's https-only client, which never carries a token (SI-04). */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .build()
}
