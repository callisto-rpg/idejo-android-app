// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Where the onboarding tour's targets are on screen (IX-18): what a step leaves uncovered
 * and points at. Pieces of several features mark themselves with [tourTarget]; the shell,
 * which draws the tour, provides [LocalTourTargets] while it runs.
 */
@Stable
class TourTargets {
    private val bounds = mutableStateMapOf<String, Rect>()

    /** [key]'s bounds in the window's root, or null when it is not on screen. */
    operator fun get(key: String): Rect? = bounds[key]

    internal fun put(
        key: String,
        rect: Rect,
    ) {
        if (bounds[key] != rect) bounds[key] = rect
    }

    internal fun remove(key: String) {
        bounds.remove(key)
    }

    /** The targets' keys. */
    companion object {
        /** The topic page's composer. */
        const val COMPOSER = "composer"

        /** **All categories** at the top of the sidebar. */
        const val ALL = "sidebar-all"

        /** A topic's name in the sidebar. */
        fun topic(id: String): String = "topic:$id"
    }
}

/** The tour's targets while it runs; null otherwise, and nothing is measured. */
val LocalTourTargets = staticCompositionLocalOf<TourTargets?> { null }

/** Marks this as the tour's [key] target (IX-18), while the tour runs. */
@Composable
fun Modifier.tourTarget(key: String): Modifier {
    val targets = LocalTourTargets.current ?: return this
    DisposableEffect(targets, key) { onDispose { targets.remove(key) } }
    return onGloballyPositioned { targets.put(key, it.boundsInRoot()) }
}
