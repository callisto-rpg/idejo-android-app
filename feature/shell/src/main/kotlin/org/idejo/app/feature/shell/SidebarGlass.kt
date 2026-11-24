// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * TRIAL (steward, 2026-11-20): the sidebar, the forum menu and the user menu over the page as
 * frosted glass. Their background lets [SidebarGlass.ALPHA] of what is behind through, and
 * that is blurred as they come in (Android 12 and later; older ones only see through). To
 * revert, set [ON] to false, or delete this file and its uses: [glassBehind] in
 * SidebarFrame, [glassBackground] and [glassEdge] in ForumFrame's SidebarPage
 * and in MenuPanel.
 */
internal object SidebarGlass {
    const val ON = true

    /** The background's opacity; text and icons stay opaque. */
    const val ALPHA = 0.6f

    /** How blurred what is behind is, once the sidebar or menu is fully in. */
    val BLUR = 16.dp

    /** The glass's lit right edge. */
    val EDGE = 1.dp
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

/**
 * Blurs this as far as [share] (0 to 1) says the glass over it has come in. Clipped to its
 * bounds: unclipped, the blur's clamped edge spilled out over what is beside it, as a band of
 * the page's background over the inbox's top bar (steward bug report, 2026-11-24).
 */
internal fun Modifier.glassBehind(
    enabled: Boolean = true,
    share: () -> Float,
): Modifier =
    if (!SidebarGlass.ON || !enabled) {
        this
    } else {
        graphicsLayer {
            clip = true
            val radius = SidebarGlass.BLUR.toPx() * share().coerceIn(0f, 1f)
            renderEffect = if (radius > 0f) BlurEffect(radius, radius) else null
        }
    }

/**
 * A pane's open edge as the rim of a glass panel: a thin line in the text's colour, brighter
 * where it starts and fading along, as light catches it. The sidebar's is its right edge,
 * from the top down; the forum menu's its [bottom] edge, from the left. Only on glass
 * ([enabled]).
 */
@Composable
internal fun Modifier.glassEdge(
    enabled: Boolean = true,
    bottom: Boolean = false,
): Modifier {
    if (!SidebarGlass.ON || !enabled) return this
    val light = MaterialTheme.colorScheme.onBackground
    val colours =
        listOf(
            light.copy(alpha = EDGE_TOP),
            light.copy(alpha = EDGE_MIDDLE),
            light.copy(alpha = EDGE_BOTTOM),
        )
    return drawWithContent {
        drawContent()
        val width = SidebarGlass.EDGE.toPx()
        if (bottom) {
            val y = size.height - width / 2
            drawLine(
                Brush.horizontalGradient(colours),
                Offset(0f, y),
                Offset(size.width, y),
                strokeWidth = width,
            )
        } else {
            val x = size.width - width / 2
            drawLine(
                Brush.verticalGradient(colours),
                Offset(x, 0f),
                Offset(x, size.height),
                strokeWidth = width,
            )
        }
    }
}

private const val EDGE_TOP = 0.35f
private const val EDGE_MIDDLE = 0.18f
private const val EDGE_BOTTOM = 0.08f
