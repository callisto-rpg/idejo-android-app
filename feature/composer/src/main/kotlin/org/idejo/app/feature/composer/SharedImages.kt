// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.idejo.app.core.data.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pictures (CO-14) and text (DL-04) shared to the app from another, read at once while the app
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

        private val mutableText = MutableStateFlow<String?>(null)

        /** Text shared to the app (DL-04): a link, a quote, until its topic is picked. */
        val text: StateFlow<String?> = mutableText.asStateFlow()

        fun offerText(text: String) {
            if (text.isNotBlank()) mutableText.value = text.trim()
        }

        fun takeText(): String? = mutableText.getAndUpdate { null }

        fun drop() {
            mutable.value = emptyList()
            mutableText.value = null
        }
    }

/** What was shared, for the app's share card (CO-14, DL-04). */
@HiltViewModel
class ShareViewModel
    @Inject
    constructor(
        private val shared: SharedImages,
        private val sessions: SessionManager,
    ) : ViewModel() {
        /** Pictures or text wait for a topic. */
        val pending: StateFlow<Boolean> =
            combine(shared.pending, shared.text) { images, text ->
                images.isNotEmpty() ||
                    text != null
            }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

        /** The forums the app holds signed in (DL-04), when there are several to pick from. */
        val forums: StateFlow<List<ShareForum>> =
            sessions.state
                .map { state ->
                    state.forums
                        .filter { it.sealedToken != null }
                        .map { ShareForum(it.origin, it.discovery.name) }
                        .takeIf { it.size > 1 }
                        .orEmpty()
                }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        /** The forum open, whose topics the card lists. */
        val here: String? get() = sessions.active?.origin

        /** Shares to [origin]'s topics instead: the app opens it, and the card again there. */
        fun forum(origin: String) {
            viewModelScope.launch { sessions.enter(origin) }
        }

        fun drop() = shared.drop()
    }

/** A forum to share to (DL-04). */
data class ShareForum(
    val origin: String,
    val name: String,
)

private const val MAX_SHARED = 10
