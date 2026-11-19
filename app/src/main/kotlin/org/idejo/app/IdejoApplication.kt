// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import org.idejo.app.core.data.AppChoices
import org.idejo.app.core.data.AppScope
import org.idejo.app.core.data.ForegroundSockets
import org.idejo.app.core.data.GroupForums
import org.idejo.app.core.data.PushRegistrar
import org.idejo.app.core.data.SessionManager
import javax.inject.Inject

/** The application, Hilt's root component: opens the forum the app had open (MD-05). */
@HiltAndroidApp
class IdejoApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject lateinit var sessions: SessionManager

    @Inject lateinit var foreground: ForegroundSockets

    /** Push for every signed-in forum (NT-02): made at launch, so it follows sign-ins. */
    @Inject lateinit var push: PushRegistrar

    @Inject lateinit var http: OkHttpClient

    /** idejo ID mode's forums, read at launch and once an hour (SI-07). */
    @Inject lateinit var groupForums: GroupForums

    @Inject lateinit var choices: AppChoices

    @Inject @AppScope
    lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        foreground.install()
        scope.launch {
            sessions.restore()
            groupForums.start(scope)
        }
    }

    /**
     * Pictures through the app's https-only client, which never carries a token (SI-04),
     * kept on disk up to the member's limit (ST-21), read once: a new limit takes the next start.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val megabytes = runBlocking(Dispatchers.IO) { choices.cacheLimitMb.first() }
        return ImageLoader
            .Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .diskCache {
                DiskCache
                    .Builder()
                    .directory(cacheDir.resolve(IMAGE_CACHE).toOkioPath())
                    .maxSizeBytes(megabytes * MEGABYTE)
                    .build()
            }.build()
    }

    private companion object {
        const val IMAGE_CACHE = "image_cache"
        const val MEGABYTE = 1000L * 1000
    }
}
