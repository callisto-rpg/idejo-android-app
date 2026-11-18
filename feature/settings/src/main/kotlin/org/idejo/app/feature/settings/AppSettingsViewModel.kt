// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.data.AppChoices
import org.idejo.app.core.data.MessageText
import org.idejo.app.core.data.PushRegistrar
import javax.inject.Inject

/** App settings' choices on this device (ST-20 to ST-22). */
@Immutable
data class AppSettingsUiState(
    /** The UnifiedPush distributors installed, by package (ST-20). */
    val distributors: List<String> = emptyList(),
    /** The one in use, by package; null when none is chosen. */
    val distributor: String? = null,
    val messageText: MessageText = MessageText.Default,
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
    /** The cache folder's size in bytes (ST-21); null until measured. */
    val cacheBytes: Long? = null,
    val cacheLimitMb: Int = org.idejo.app.core.data.DEFAULT_CACHE_MB,
    /** **Clear cache** is at work. */
    val clearing: Boolean = false,
)

/** What the member does in App settings. */
sealed interface AppSettingsEvent {
    data class UseDistributor(
        val distributor: String,
    ) : AppSettingsEvent

    data class SetMessageText(
        val choice: MessageText,
    ) : AppSettingsEvent

    data class SetHaptics(
        val on: Boolean,
    ) : AppSettingsEvent

    data class SetReduceMotion(
        val on: Boolean,
    ) : AppSettingsEvent

    data class SetCacheLimit(
        val megabytes: Int,
    ) : AppSettingsEvent

    data object ClearCache : AppSettingsEvent
}

/**
 * App settings (ST-20 to ST-22), kept on this device for every forum: the push distributor
 * (UnifiedPush's own list: the embedded FCM distributor in Play is one), text size inside
 * messages, haptics, reduced motion, and the cache. Mode (MD-04) and opening links in the
 * app (DL-01) come with M4 and M5.
 */
@HiltViewModel
class AppSettingsViewModel
    @Inject
    constructor(
        private val choices: AppChoices,
        private val registrar: PushRegistrar,
        private val cache: AppCache,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(AppSettingsUiState())
        val state: StateFlow<AppSettingsUiState> = mutableState.asStateFlow()

        init {
            distributors()
            viewModelScope.launch {
                combine(
                    choices.display,
                    choices.cacheLimitMb,
                ) { display, limit -> display to limit }
                    .collect { (display, limit) ->
                        mutableState.update {
                            it.copy(
                                messageText = display.messageText,
                                haptics = display.haptics,
                                reduceMotion = display.reduceMotion,
                                cacheLimitMb = limit,
                            )
                        }
                    }
            }
            measure()
        }

        fun onEvent(event: AppSettingsEvent) {
            when (event) {
                is AppSettingsEvent.UseDistributor -> {
                    registrar.use(event.distributor)
                    distributors()
                }

                is AppSettingsEvent.SetMessageText -> {
                    viewModelScope.launch { choices.messageText(event.choice) }
                }

                is AppSettingsEvent.SetHaptics -> {
                    viewModelScope.launch { choices.haptics(event.on) }
                }

                is AppSettingsEvent.SetReduceMotion -> {
                    viewModelScope.launch { choices.reduceMotion(event.on) }
                }

                is AppSettingsEvent.SetCacheLimit -> {
                    viewModelScope.launch { choices.cacheLimitMb(event.megabytes) }
                }

                AppSettingsEvent.ClearCache -> {
                    clear()
                }
            }
        }

        /** Read again on returning: a distributor may have been installed meanwhile. */
        fun distributors() {
            val (installed, current) = registrar.distributors()
            mutableState.update { it.copy(distributors = installed, distributor = current) }
        }

        private fun measure() {
            viewModelScope.launch {
                val bytes = cache.size()
                mutableState.update { it.copy(cacheBytes = bytes) }
            }
        }

        private fun clear() {
            if (mutableState.value.clearing) return
            mutableState.update { it.copy(clearing = true) }
            viewModelScope.launch {
                cache.clear()
                mutableState.update { it.copy(clearing = false, cacheBytes = cache.size()) }
            }
        }
    }
