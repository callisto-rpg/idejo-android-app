// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import android.app.Activity
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
import org.idejo.app.core.data.AppMode
import org.idejo.app.core.data.MessageText
import org.idejo.app.core.data.PushRegistrar
import org.idejo.app.core.data.SessionManager
import javax.inject.Inject

/** App settings' choices on this device (ST-20 to ST-22). */
@Immutable
data class AppSettingsUiState(
    /** The UnifiedPush distributors installed, by package (ST-20). */
    val distributors: List<String> = emptyList(),
    /** The one in use, by package; null when none is chosen. */
    val distributor: String? = null,
    /** Push turned off on this device (NT-06). */
    val pushOff: Boolean = false,
    /** **Enable** found no distributor: Sunup is offered (NT-06). */
    val offerSunup: Boolean = false,
    /** Sunup's store page was opened: enabling is tried again on returning. */
    val awaitingSunup: Boolean = false,
    val messageText: MessageText = MessageText.Default,
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
    /** The cache folder's size in bytes (ST-21); null until measured. */
    val cacheBytes: Long? = null,
    val cacheLimitMb: Int = org.idejo.app.core.data.DEFAULT_CACHE_MB,
    /** **Clear cache** is at work. */
    val clearing: Boolean = false,
    /** The app's mode (MD-04); null until read. */
    val mode: AppMode? = null,
    /** The forums held, for the forum kept when leaving idejo ID mode. */
    val forums: List<HeldForum> = emptyList(),
    /** idejo ID mode's inbox, by name. */
    val inboxName: String? = null,
    /** The forum open, kept by default. */
    val current: String? = null,
) {
    /** Push is on, through [distributor]. */
    val pushOn: Boolean get() = !pushOff && distributor != null
}

/** A forum the app holds, for **Mode** (MD-04). */
@Immutable
data class HeldForum(
    val origin: String,
    val name: String,
)

/** What the member does in App settings. */
sealed interface AppSettingsEvent {
    data class UseDistributor(
        val distributor: String,
    ) : AppSettingsEvent

    /** **Turn off** push on this device: every forum's registration ends (NT-06). */
    data object DisablePush : AppSettingsEvent

    /** Sunup offered: its store page opened ([install]), or **Not now**. */
    data class AnswerSunup(
        val install: Boolean,
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

    /** MD-04: to single-forum mode, keeping [keep] (none: the welcome screen). */
    data class LeaveIdejoId(
        val keep: String?,
    ) : AppSettingsEvent
}

/**
 * App settings (ST-20 to ST-22), kept on this device for every forum: the push distributor
 * (UnifiedPush's own list: the embedded FCM distributor in Play is one), text size inside
 * messages, haptics, reduced motion, and the cache; the mode (MD-04). Opening links in the
 * app (DL-01) comes with M5.
 */
@HiltViewModel
class AppSettingsViewModel
    @Inject
    constructor(
        private val choices: AppChoices,
        private val registrar: PushRegistrar,
        private val cache: AppCache,
        private val sessions: SessionManager,
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
            viewModelScope.launch {
                choices.pushOff.collect { off -> mutableState.update { it.copy(pushOff = off) } }
            }
            viewModelScope.launch {
                sessions.state.collect { app ->
                    mutableState.update {
                        it.copy(
                            mode = app.mode,
                            forums =
                                app.forums.map { forum ->
                                    HeldForum(forum.origin, forum.name)
                                },
                            inboxName = app.inbox?.name,
                            current = sessions.active?.origin?.takeIf { o -> app.forum(o) != null },
                        )
                    }
                }
            }
        }

        fun onEvent(event: AppSettingsEvent) {
            when (event) {
                is AppSettingsEvent.UseDistributor -> {
                    registrar.use(event.distributor)
                    distributors()
                }

                AppSettingsEvent.DisablePush -> {
                    viewModelScope.launch {
                        choices.pushOff(true)
                        registrar.disable()
                        distributors()
                    }
                }

                is AppSettingsEvent.AnswerSunup -> {
                    mutableState.update {
                        it.copy(offerSunup = false, awaitingSunup = event.install)
                    }
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

                is AppSettingsEvent.LeaveIdejoId -> {
                    viewModelScope.launch { sessions.leaveIdejoId(event.keep) }
                }
            }
        }

        /**
         * **Enable push notifications** (NT-06), after Android's notification permission:
         * push back on, through the distributor there is or the default one (an installed
         * one before the embedded FCM); none at all, Sunup is offered.
         */
        fun enablePush(activity: Activity) {
            mutableState.update { it.copy(awaitingSunup = false) }
            viewModelScope.launch {
                choices.pushOff(false)
                registrar.enable(activity) { found ->
                    distributors()
                    if (!found) mutableState.update { it.copy(offerSunup = true) }
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
