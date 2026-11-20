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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.idejo.app.core.designsystem.theme.IdejoTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

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
 * the right across the page starts, or as the back gesture moves (gesture navigation);
 * letting go after moving right opens it over the whole screen, after moving left shuts it.
 * **Sidebar** in the forum menu, and the forum's logo on the home page, open it too. A drag
 * to the left across it closes it the same way, as does back. In an expanded window the sidebar stays open beside the page. Across a [fold]
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
    val pulling = rememberPulling(sliding)
    Scaffold(
        modifier.glassBehind(enabled = !wide && fold == null) {
            if (sliding.width > 0f) sliding.shown.value / sliding.width else 0f
        },
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
                    .nestedScroll(pulling)
                    .reveal(sliding),
            ) { shownPage() }
        }
    }
    if (!wide && fold == null) {
        BackHandler(enabled = open, onBack = onClose)
        BackGestureOpens(open, sliding) { opened() }
        LaunchedEffect(open, sliding.width) { sliding.settle(open) }
        if (sliding.shown.value > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .offset { IntOffset((sliding.shown.value - sliding.width).roundToInt(), 0) }
                    .testTag(OVER)
                    .reveal(sliding),
            ) { shownSidebar(false) }
        }
    }
}

/**
 * How much of the sidebar is drawn over the page, from 0 (hidden) to [width] (open). A drag
 * moves it with the finger; letting go settles it open or shut and tells the frame.
 */
internal class Sliding(
    private val scope: CoroutineScope,
    private val reduced: () -> Boolean,
    private val onOpen: () -> Unit,
    private val onClose: () -> Unit,
) {
    val shown = Animatable(0f)
    var width = 0f

    /** Drawn at [progress] (0 to 1) of the way, as a back gesture moves. */
    fun peek(progress: Float) {
        scope.launch { shown.snapTo(width * progress.coerceIn(0f, 1f)) }
    }

    /** The way the finger last moved: 1 right, -1 left, 0 not yet. */
    private var heading = 0f

    /**
     * One drag's move by [delta] px (positive to the right): the sidebar follows the finger,
     * held where it is while the finger rests. Opening and closing are the same drag.
     */
    fun drag(delta: Float) {
        if (abs(delta) >= JITTER) heading = sign(delta)
        scope.launch { shown.snapTo((shown.value + delta).coerceIn(0f, width)) }
    }

    /**
     * Let go: the way the finger last moved decides, right opens and left closes; a drag
     * that never moved settles where it is nearer.
     */
    fun release() {
        val open = if (heading == 0f) shown.value > width / 2 else heading > 0f
        heading = 0f
        if (open) onOpen() else onClose()
        settle(open)
    }

    fun settle(open: Boolean) {
        val target = if (open) width else 0f
        scope.launch { if (reduced()) shown.snapTo(target) else shown.animateTo(target) }
    }
}

/**
 * A sideways swipe a child scroller leaves over (the home page's tabs, swiped right on the
 * leftmost one) pulls the sidebar as a drag across the page would; once pulling, the swipe
 * back is the sidebar's too, not the child's.
 */
@Composable
private fun rememberPulling(sliding: Sliding): NestedScrollConnection {
    return remember(sliding) {
        object : NestedScrollConnection {
            var pulling = false

            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!pulling || source != NestedScrollSource.UserInput) return Offset.Zero
                sliding.drag(available.x)
                return Offset(available.x, 0f)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || available.x <= 0f) return Offset.Zero
                pulling = true
                sliding.drag(available.x)
                return Offset(available.x, 0f)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!pulling) return Velocity.Zero
                pulling = false
                sliding.release()
                return available
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
 * Drags move the sidebar with the finger (SH-30): across the page to open it (a category,
 * a topic; the home page's tabs hand theirs on, see [rememberPulling]), across the sidebar
 * to close it. A child that scrolls sideways keeps its drags. Both are the same drag
 * ([Sliding]): the way it last moved decides.
 */
private fun Modifier.reveal(sliding: Sliding): Modifier =
    pointerInput(sliding) {
        detectHorizontalDragGestures(
            onDragEnd = sliding::release,
            onDragCancel = sliding::release,
        ) { change, delta ->
            change.consume()
            sliding.drag(delta)
        }
    }

/** A move smaller than this (px) is the finger's tremble, not a change of way. */
private const val JITTER = 1f
