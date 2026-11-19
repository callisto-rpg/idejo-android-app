// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The last place opened (MD-05): each forum's page (its route, with the topic's message at
 * the top of the screen), and the inbox's tab, kept as the member goes, [settle] after the
 * last move. On the next launch the session restored opens there, once.
 */
@OptIn(FlowPreview::class)
class LastPlaces(
    private val sessions: SessionManager,
    private val store: ForumStore,
    scope: CoroutineScope,
    settle: Duration = 1.seconds,
) {
    /** The app's state as this launch found it, read before anything is kept anew. */
    private val found = scope.async(start = CoroutineStart.LAZY) { store.current() }
    private val routeTaken = AtomicBoolean(false)
    private val tabTaken = AtomicBoolean(false)
    private val pending = MutableStateFlow<Map<String, String?>>(emptyMap())

    init {
        scope.launch {
            found.await()
            pending.debounce(settle).collect { places ->
                places.forEach { (origin, route) ->
                    if (store.current().record(origin)?.lastPlace != route) {
                        sessions.updateForum(origin) { it.copy(lastPlace = route) }
                    }
                }
            }
        }
    }

    /** The page now open at [origin]: its [route], null for its home. */
    fun keep(
        origin: String,
        route: String?,
    ) = pending.update { it + (origin to route) }

    /** The inbox's tab now shown. */
    suspend fun keepInboxTab(tab: String) {
        found.await()
        if (store.current().inboxTab != tab) store.update { it.copy(inboxTab = tab) }
    }

    /**
     * The page to open at [origin], once per launch, for the session the launch restored (the
     * forum, or the inbox, open when the app was left); null otherwise.
     */
    suspend fun atLaunch(origin: String): String? {
        val state = found.await()
        if (origin != restored(state) || !routeTaken.compareAndSet(false, true)) return null
        return state.record(origin)?.lastPlace
    }

    /** The inbox's tab to show, once per launch, when the launch restored the inbox. */
    suspend fun inboxTabAtLaunch(): String? {
        val state = found.await()
        val inbox = state.inbox?.origin ?: return null
        if (inbox != restored(state) || !tabTaken.compareAndSet(false, true)) return null
        return state.inboxTab
    }

    /** The session `SessionManager.restore` opened from [state]. */
    private fun restored(state: AppState): String? =
        when {
            state.mode == AppMode.IdejoId && state.inbox != null -> {
                state.forum(state.active)?.origin ?: state.inbox.origin
            }

            else -> {
                (state.forum(state.active) ?: state.forums.firstOrNull())?.origin
            }
        }
}
