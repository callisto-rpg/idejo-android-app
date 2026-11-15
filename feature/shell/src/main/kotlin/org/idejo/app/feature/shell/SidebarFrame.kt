// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.idejo.app.core.designsystem.theme.IdejoTheme

/**
 * Whether the window is wide enough to keep the sidebar beside the page (SH-60): the
 * expanded width class, 840 dp and more, as the web on a computer.
 */
@Composable
internal fun wideWindow(): Boolean {
    val width = LocalWindowInfo.current.containerSize.width
    return with(LocalDensity.current) { width.toDp() } >= EXPANDED
}

/** A fold that separates the screen (SH-61): its edges in the window, along its axis. */
data class Fold(
    val vertical: Boolean,
    val start: Dp,
    val end: Dp,
)

/** The separating fold of a foldable's window (book or tabletop posture), or null. */
@Composable
internal fun currentFold(): Fold? {
    val hinge =
        currentWindowAdaptiveInfo()
            .windowPosture.hingeList
            .firstOrNull { it.isSeparating } ?: return null
    return with(LocalDensity.current) {
        val bounds = hinge.bounds
        if (hinge.isVertical) {
            Fold(true, bounds.left.toDp(), bounds.right.toDp())
        } else {
            Fold(false, bounds.top.toDp(), bounds.bottom.toDp())
        }
    }
}

/**
 * The page under the top bar, and the sidebar (SH-30, SH-60, SH-61). On a phone (compact and
 * medium widths) a swipe to the right across the page, or the top bar's sidebar button,
 * opens the sidebar over the whole screen; a swipe to the left, back or its close button
 * closes it. In an expanded window the sidebar stays open beside the page. Across a [fold]
 * the two take a side each, so no message is ever under the hinge: in book posture the
 * sidebar on the left, the page on the right; in tabletop posture the page above.
 * [sidebar] is told whether it is docked beside the page.
 */
@Composable
internal fun SidebarFrame(
    wide: Boolean,
    fold: Fold?,
    open: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    topBar: @Composable () -> Unit,
    sidebar: @Composable (docked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    page: @Composable () -> Unit,
) = Box(Modifier.fillMaxSize()) {
    // Kept across a change of width (a fold opened): the page keeps its state.
    val shownPage = remember(page) { movableContentOf(page) }
    val shownSidebar = remember(sidebar) { movableContentOf(sidebar) }
    Scaffold(
        modifier,
        topBar = topBar,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (fold != null) {
            if (fold.vertical) {
                Book(fold, shownSidebar, Modifier.padding(padding), shownPage)
            } else {
                Tabletop(fold, shownSidebar, Modifier.padding(padding), shownPage)
            }
        } else if (wide) {
            Row(Modifier.padding(padding).fillMaxSize()) {
                Box(
                    Modifier.width(SIDEBAR_WIDTH).fillMaxHeight().testTag(DOCKED),
                ) { shownSidebar(true) }
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight()) { shownPage() }
            }
        } else {
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .testTag(PAGE)
                    .swipe(right = true, onSwipe = onOpen),
            ) { shownPage() }
        }
    }
    if (!wide && fold == null) {
        BackHandler(enabled = open, onBack = onClose)
        Slide(open, horizontal = true) {
            Box(Modifier.fillMaxSize().testTag(OVER).swipe(right = false, onSwipe = onClose)) {
                shownSidebar(false)
            }
        }
    }
}

/** Where [fold] is in a frame placed at [origin], and how wide it is. */
private fun Fold.within(origin: Dp): Pair<Dp, Dp> =
    (start - origin).coerceAtLeast(0.dp) to end - start

/** The frame's origin in the window, along [fold]'s axis. */
@Composable
private fun rememberOrigin(fold: Fold): Pair<Dp, Modifier> {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val at = with(LocalDensity.current) { if (fold.vertical) origin.x.toDp() else origin.y.toDp() }
    return at to Modifier.onGloballyPositioned { origin = it.positionInWindow() }
}

/** Book posture: the sidebar left of the hinge, the page right of it (SH-61). */
@Composable
private fun Book(
    fold: Fold,
    sidebar: @Composable (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    page: @Composable () -> Unit,
) {
    val (origin, placed) = rememberOrigin(fold)
    val (before, gap) = fold.within(origin)
    Row(modifier.fillMaxSize().then(placed)) {
        Box(Modifier.width(before).fillMaxHeight().testTag(DOCKED)) { sidebar(true) }
        Spacer(Modifier.width(gap))
        Box(Modifier.weight(1f).fillMaxHeight()) { page() }
    }
}

/** Tabletop posture: the page above the hinge, the sidebar below it (SH-61). */
@Composable
private fun Tabletop(
    fold: Fold,
    sidebar: @Composable (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    page: @Composable () -> Unit,
) {
    val (origin, placed) = rememberOrigin(fold)
    val (before, gap) = fold.within(origin)
    Column(modifier.fillMaxSize().then(placed)) {
        Box(Modifier.height(before).fillMaxWidth()) { page() }
        Spacer(Modifier.height(gap))
        Box(Modifier.weight(1f).fillMaxWidth().testTag(DOCKED)) { sidebar(true) }
    }
}

/** Test tags: the page, the sidebar over it, the sidebar beside it. */
internal const val PAGE = "page"
internal const val OVER = "sidebar-over"
internal const val DOCKED = "sidebar-docked"

private val EXPANDED = 840.dp
private val SIDEBAR_WIDTH = 320.dp

/** A full-screen page sliding in from the left (the sidebar) or down from the top bar. */
@Composable
internal fun Slide(
    visible: Boolean,
    horizontal: Boolean,
    content: @Composable () -> Unit,
) {
    val reduced = IdejoTheme.reducedMotion
    val enter: EnterTransition =
        when {
            reduced -> EnterTransition.None
            horizontal -> slideInHorizontally { -it }
            else -> slideInVertically { -it }
        }
    val exit: ExitTransition =
        when {
            reduced -> ExitTransition.None
            horizontal -> slideOutHorizontally { -it }
            else -> slideOutVertically { -it }
        }
    AnimatedVisibility(visible, enter = enter, exit = exit) { content() }
}

/**
 * A quick swipe to the [right] (or to the left) across the page, not from the screen's
 * edges, which belong to system back (SH-30). A child that scrolls sideways keeps its drags.
 */
private fun Modifier.swipe(
    right: Boolean,
    onSwipe: () -> Unit,
): Modifier =
    pointerInput(right) {
        val edge = EDGE.toPx()
        val distance = SWIPE.toPx()
        var travelled = 0f
        var counted = false
        detectHorizontalDragGestures(
            onDragStart = { start ->
                travelled = 0f
                counted = start.x > edge && start.x < size.width - edge
            },
            onDragEnd = {
                if (counted && (if (right) travelled > distance else travelled < -distance)) {
                    onSwipe()
                }
            },
        ) { _, delta -> travelled += delta }
    }

private val EDGE = 32.dp
private val SWIPE = 72.dp
