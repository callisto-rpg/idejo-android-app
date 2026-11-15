// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pictures shared to the app from another (CO-14's share sheet), read at once while the app
 * may read them, until the member picks the topic they go to; then that topic's composer
 * takes them.
 */
@Singleton
class SharedImages
    @Inject
    constructor() {
        private val mutable = MutableStateFlow<List<ImageFile>>(emptyList())
        val pending: StateFlow<List<ImageFile>> = mutable.asStateFlow()

        /** The pictures at [uris], the ones that are pictures; at most [MAX_SHARED]. */
        suspend fun offer(
            resolver: ContentResolver,
            uris: List<Uri>,
        ) {
            val images =
                withContext(Dispatchers.IO) {
                    uris.take(MAX_SHARED).mapNotNull { readImage(resolver, it) }
                }
            if (images.isNotEmpty()) mutable.value = images
        }

        fun take(): List<ImageFile> = mutable.getAndUpdate { emptyList() }

        fun drop() {
            mutable.value = emptyList()
        }
    }

/** The shared pictures, for the app's topic card. */
@HiltViewModel
class ShareViewModel
    @Inject
    constructor(
        private val shared: SharedImages,
    ) : ViewModel() {
        val pending: StateFlow<List<ImageFile>> = shared.pending

        fun drop() = shared.drop()
    }

private const val MAX_SHARED = 10
