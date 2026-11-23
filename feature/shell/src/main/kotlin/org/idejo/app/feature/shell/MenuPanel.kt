// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A menu under the top bar, which stays in view with its breadcrumbs: the forum menu (SH-10)
 * and the user menu (SH-20), the same way (steward requests, 2026-11-22). As tall as its
 * contents, at most the rest of the screen, where they scroll, so the page shows below a
 * short one. At its foot, the sidebar's glass line and a pill saying it can be pulled. It
 * follows the finger as the sidebar does ([Sliding]): a pull down on the top bar brings it
 * down (from the member's picture, the user menu), a drag up on it takes it back, and
 * letting go, the way the finger last moved decides. A tap on the page below it closes it,
 * as back does. [top] is where the top bar ends; [tag] names it for tests.
 */
@Composable
internal fun MenuPanel(
    sliding: Sliding,
    open: Boolean,
    top: Dp,
    tag: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(open, sliding.width, sliding.dragging) { sliding.settle(open) }
    if (!open && !sliding.dragging && sliding.shown.value <= 0f) return
    val pulling = rememberPanelPulling(sliding)
    Box(
        Modifier
            .fillMaxSize()
            .padding(top = top)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            ).clipToBounds(),
    ) {
        // The page below a short menu: a tap closes it.
        Box(Modifier.fillMaxSize().pointerInput(onClose) { detectTapGestures { onClose() } })
        Surface(
            Modifier
                .fillMaxWidth()
                .testTag(tag)
                .onSizeChanged { sliding.width = it.height.toFloat() }
                .offset { IntOffset(0, (sliding.shown.value - sliding.width).roundToInt()) }
                .glassEdge(bottom = true)
                .pointerInput(sliding) {
                    detectVerticalDragGestures(
                        onDragEnd = sliding::release,
                        onDragCancel = sliding::release,
                    ) { change, delta ->
                        change.consume()
                        sliding.drag(delta)
                    }
                }.nestedScroll(pulling),
            // Frosted glass while the trial lasts (SidebarGlass).
            color = glassBackground(),
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Column {
                Box(Modifier.weight(1f, fill = false)) { content() }
                // Says the menu can be pulled; back and a tap below close it too (AX-04).
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 4.dp, bottom = 10.dp)
                        .size(PILL_WIDTH, PILL_HEIGHT)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                )
            }
        }
    }
}

/**
 * A drag up the menu's contents once they cannot scroll further takes the menu back with the
 * finger; once taking it, the drag back down is the menu's too, as the sidebar's pull.
 */
@Composable
private fun rememberPanelPulling(sliding: Sliding): NestedScrollConnection =
    remember(sliding) {
        object : NestedScrollConnection {
            var pulling = false

            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!pulling || source != NestedScrollSource.UserInput) return Offset.Zero
                sliding.drag(available.y)
                return Offset(0f, available.y)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
                pulling = true
                sliding.drag(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!pulling) return Velocity.Zero
                pulling = false
                sliding.release()
                return available
            }
        }
    }

/** Test tag: the forum menu under the top bar. */
internal const val FORUM_MENU = "forum-menu"

/** Test tag: the user menu under the top bar. */
internal const val USER_MENU = "user-menu"

private val PILL_WIDTH = 36.dp
private val PILL_HEIGHT = 4.dp

/**
 * A pull down that starts on this (a top bar, SH-01), seen before anything under it, so the
 * pull always wins: the menu [menuAt] gives for where it started (the forum menu, or from the
 * member's picture the user menu) comes down with the finger until it lets go. [menuAt] is
 * read when a pull starts, so it may read state.
 */
internal fun Modifier.pullDown(menuAt: (Offset) -> Sliding): Modifier =
    pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var moved = Offset.Zero
            var pulled = false
            // Watched until let go, pulled down, or dragged sideways.
            var watching = true
            while (watching) {
                val change =
                    awaitPointerEvent(PointerEventPass.Initial)
                        .changes
                        .firstOrNull { it.id == down.id }
                moved += change?.positionChange() ?: Offset.Zero
                pulled = moved.y > slop && moved.y > abs(moved.x)
                if (pulled) change?.consume()
                watching = change?.pressed == true && !pulled && abs(moved.x) <= slop
            }
            if (!pulled) return@awaitEachGesture
            val menu = menuAt(down.position)
            menu.drag(moved.y)
            while (true) {
                val change =
                    awaitPointerEvent(PointerEventPass.Initial)
                        .changes
                        .firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) break
                menu.drag(change.positionChange().y)
                change.consume()
            }
            menu.release()
        }
    }
