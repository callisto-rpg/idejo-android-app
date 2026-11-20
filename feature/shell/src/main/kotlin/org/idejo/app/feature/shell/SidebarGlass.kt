// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * TRIAL (steward, 2026-11-20): the sidebar, the forum menu and the user menu over the page as
 * frosted glass. Their background lets [SidebarGlass.ALPHA] of what is behind through, and
 * that is blurred as they come in (Android 12 and later; older ones only see through). To
 * revert, set [ON] to false, or delete this file and its uses: [glassBehind] in
 * SidebarFrame and ForumFrame, [glassBackground] in ForumFrame's SidebarPage and in
 * MenuPage.
 */
internal object SidebarGlass {
    const val ON = true

    /** The background's opacity; text and icons stay opaque. */
    const val ALPHA = 0.6f

    /** How blurred what is behind is, once the sidebar or menu is fully in. */
    val BLUR = 16.dp
}

/**
 * The glass's background, [opaque] where it stands beside the page (docked). Its text and
 * icons take `onBackground` explicitly: a see-through colour has no content colour of its
 * own, so Material would fall back to black.
 */
@Composable
internal fun glassBackground(opaque: Boolean = false): Color =
    MaterialTheme.colorScheme.background.copy(
        alpha = if (SidebarGlass.ON && !opaque) SidebarGlass.ALPHA else 1f,
    )

/** Blurs this as far as [share] (0 to 1) says the glass over it has come in. */
internal fun Modifier.glassBehind(
    enabled: Boolean = true,
    share: () -> Float,
): Modifier =
    if (!SidebarGlass.ON || !enabled) {
        this
    } else {
        graphicsLayer {
            val radius = SidebarGlass.BLUR.toPx() * share().coerceIn(0f, 1f)
            renderEffect = if (radius > 0f) BlurEffect(radius, radius) else null
        }
    }
