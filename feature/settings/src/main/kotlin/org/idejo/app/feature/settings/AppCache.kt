// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import android.content.Context
import coil3.SingletonImageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** What the app keeps in its cache folder (ST-21): its size, and **Clear cache**. */
interface AppCache {
    suspend fun size(): Long

    suspend fun clear()
}

/**
 * The app's cache folder: the image cache (Coil's, cleared through it), pictures handed to
 * other apps, the WebViews' files. Pictures taken for a message not sent yet stay.
 */
class AppCacheFolder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AppCache {
        override suspend fun size(): Long = withContext(Dispatchers.IO) { context.cacheDir.bytes() }

        override suspend fun clear() =
            withContext(Dispatchers.IO) {
                val images = SingletonImageLoader.get(context)
                images.memoryCache?.clear()
                images.diskCache?.clear()
                context.cacheDir
                    .listFiles()
                    .orEmpty()
                    .filter { it.name !in KEPT }
                    .forEach { it.deleteRecursively() }
            }

        private fun File.bytes(): Long = walkTopDown().filter { it.isFile }.sumOf { it.length() }

        private companion object {
            /** The camera's pictures for the composer (CO-14), and Coil's own folder. */
            val KEPT = setOf("camera", "image_cache")
        }
    }
