// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.idejo.app.core.designsystem.theme.IdejoTheme
import kotlin.math.roundToInt

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
 * medium widths) the sidebar comes in from the left under the finger as soon as a drag to
 * the right across the page starts, so it can be peeked at; let go past a third of the way,
 * or flung, it opens over the whole screen, else it slides back. **Sidebar** in the forum
 * menu opens it too. A drag to the left closes it the same way, as do back and its close
 * button. In an expanded window the sidebar stays open beside the page. Across a [fold]
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
    val scope = rememberCoroutineScope()
    val reduced by rememberUpdatedState(IdejoTheme.reducedMotion)
    val opened by rememberUpdatedState(onOpen)
    val closed by rememberUpdatedState(onClose)
    val sliding = remember { Sliding(scope, { reduced }, { opened() }, { closed() }) }
    // The sidebar over the page is as wide as the window.
    sliding.width =
        LocalWindowInfo.current.containerSize.width
            .toFloat()
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
                    .reveal(sliding, opening = true),
            ) { shownPage() }
        }
    }
    if (!wide && fold == null) {
        BackHandler(enabled = open, onBack = onClose)
        LaunchedEffect(open, sliding.width) { sliding.settle(open) }
        if (sliding.shown.value > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .offset { IntOffset((sliding.shown.value - sliding.width).roundToInt(), 0) }
                    .testTag(OVER)
                    .reveal(sliding, opening = false),
            ) { shownSidebar(false) }
        }
    }
}

/**
 * How much of the sidebar is drawn over the page, from 0 (hidden) to [width] (open). A drag
 * moves it with the finger; letting go settles it open or shut and tells the frame.
 */
private class Sliding(
    private val scope: CoroutineScope,
    private val reduced: () -> Boolean,
    private val onOpen: () -> Unit,
    private val onClose: () -> Unit,
) {
    val shown = Animatable(0f)
    var width = 0f

    fun drag(delta: Float) {
        scope.launch { shown.snapTo((shown.value + delta).coerceIn(0f, width)) }
    }

    /** Let go with [velocity] (px/s, positive to the right) while [opening] or closing. */
    fun release(
        opening: Boolean,
        velocity: Float,
        fling: Float,
    ) {
        val open =
            when {
                velocity > fling -> true
                velocity < -fling -> false
                opening -> shown.value > width * OPENS_PAST
                else -> shown.value > width * (1 - OPENS_PAST)
            }
        if (open) onOpen() else onClose()
        settle(open)
    }

    fun settle(open: Boolean) {
        val target = if (open) width else 0f
        scope.launch { if (reduced()) shown.snapTo(target) else shown.animateTo(target) }
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
 * Drags across the page move the sidebar with the finger (SH-30), not those from the screen's
 * edges, which belong to system back. A child that scrolls sideways keeps its drags.
 */
private fun Modifier.reveal(
    sliding: Sliding,
    opening: Boolean,
): Modifier =
    pointerInput(opening) {
        val edge = EDGE.toPx()
        val fling = FLING.toPx()
        val tracker = VelocityTracker()
        var counted = false
        val end = {
            if (counted) sliding.release(opening, tracker.calculateVelocity().x, fling)
            counted = false
        }
        detectHorizontalDragGestures(
            onDragStart = { start ->
                tracker.resetTracking()
                counted = start.x > edge && start.x < size.width - edge
            },
            onDragEnd = end,
            onDragCancel = end,
        ) { change, delta ->
            if (counted) {
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                sliding.drag(delta)
            }
        }
    }

private val EDGE = 32.dp

/** Let go past this share of its width, the sidebar opens (closing: back under it, it shuts). */
private const val OPENS_PAST = 1f / 3

/** A fling this fast (per second) opens or closes the sidebar wherever it is. */
private val FLING = 400.dp
