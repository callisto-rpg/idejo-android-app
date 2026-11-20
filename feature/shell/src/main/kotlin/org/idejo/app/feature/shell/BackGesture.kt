// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.activity.BackEventCompat
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CancellationException

/**
 * While the sidebar is shut, the back gesture (from either edge) pulls it in under the
 * finger and [onOpen]s it; the back button, which brings no swipe, still goes back.
 */
@Composable
internal fun BackGestureOpens(
    open: Boolean,
    sliding: Sliding,
    onOpen: () -> Unit,
) {
    var passing by remember { mutableStateOf(false) }
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    PredictiveBackHandler(enabled = !open && !passing) { events ->
        var swiped = false
        try {
            events.collect { event ->
                if (event.swipeEdge != BackEventCompat.EDGE_NONE) {
                    swiped = true
                    sliding.peek(event.progress)
                }
            }
            if (swiped) {
                onOpen()
                sliding.settle(true)
            } else {
                // Not ours: let the next handler (or the system) go back.
                passing = true
                withFrameNanos {}
                withFrameNanos {}
                dispatcher?.onBackPressed()
                passing = false
            }
        } catch (cancelled: CancellationException) {
            sliding.settle(false)
            throw cancelled
        }
    }
}
