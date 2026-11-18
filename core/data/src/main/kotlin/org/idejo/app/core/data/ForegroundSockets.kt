// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the open forum's socket connected while the app is in the foreground and closes it
 * 30 seconds after the app goes to the background (NF-04; growing-message invariant 7: the
 * server then closes an open message, as when a browser tab closes).
 */
class ForegroundSockets(
    private val sessions: SessionManager,
    private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    @Volatile private var foreground = false

    /** The app is on screen: live bubbles show what pushes would (NT-01, NT-09). */
    val inForeground: Boolean get() = foreground
    private var closing: Job? = null

    fun install() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            sessions.launch.collectLatest { launch ->
                if (foreground && launch is Launch.Forum) launch.session.connectIfSignedIn()
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        foreground = true
        closing?.cancel()
        sessions.active?.connectIfSignedIn()
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground = false
        closing =
            scope.launch {
                delay(BACKGROUND_GRACE)
                sessions.active?.socket?.disconnect()
            }
    }

    private fun ForumSession.connectIfSignedIn() {
        if (access.value == Access.SignedIn) socket.connect()
    }

    private companion object {
        val BACKGROUND_GRACE = 30.seconds
    }
}
