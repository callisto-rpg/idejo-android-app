// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.idejo.app.core.data.OnboardingTour
import org.idejo.app.core.designsystem.component.TourTargets
import org.idejo.app.core.designsystem.theme.IdejoTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The onboarding tour's steps in the app (IX-18; steward request, 2026-11-22), as the web's
 * with the app's ways: the sidebar opens with a swipe, and three Enters complete a message.
 */
internal enum class TourStep {
    /** "Swipe to open the sidebar", a hand swiping right. */
    Swipe,

    /** The sidebar open: the onboarding topic in it, uncovered. */
    OpenTopic,

    /** On the onboarding topic: the composer, "Type “Hi, I’m …” then press Enter". */
    SayHi,

    /** The first message sent, open: the question, then Enter×3. */
    Present,

    /** The message completed: **All categories** at the top of the sidebar, and **Close**. */
    Finished,
}

/** The step [tour] is at, from where the member is; null for none. */
internal fun tourStep(
    tour: OnboardingTour.State,
    onTopic: Boolean,
    sidebarShown: Boolean,
): TourStep? =
    when {
        tour.finished -> TourStep.Finished
        !tour.active -> null
        onTopic && tour.open -> TourStep.Present
        onTopic -> TourStep.SayHi
        sidebarShown -> TourStep.OpenTopic
        else -> TourStep.Swipe
    }

/** What the tour's cards say and do. */
internal class TourActions(
    /** The swipe step's drag, to the right: the sidebar opens. */
    val onSwipe: () -> Unit,
    /** The onboarding topic, when its row is not on screen to tap. */
    val onOpenTopic: () -> Unit,
    /** **Close** on the last card. */
    val onClose: () -> Unit,
)

/**
 * The tour over the whole frame: the page under its background colour at half opacity, but
 * for the step's target ([TourTargets]), which a glowing outline marks; a card beside it with
 * an arrow pointing at it; and every touch elsewhere stopped. A step whose target is not on
 * screen yet (the composer still loading) draws nothing and stops nothing.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod") // Each step's target, card and touches.
@Composable
internal fun TourOverlay(
    step: TourStep,
    tour: OnboardingTour.State,
    name: String,
    allLabel: String,
    targets: TourTargets,
    actions: TourActions,
) {
    val onboarding = tour.onboarding
    var origin by remember { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    val key =
        when (step) {
            TourStep.Swipe -> null
            TourStep.OpenTopic -> onboarding?.let { TourTargets.topic(it.topicId) }
            TourStep.SayHi, TourStep.Present -> TourTargets.COMPOSER
            TourStep.Finished -> TourTargets.ALL
        }
    val pad = with(LocalDensity.current) { HOLE_PAD.toPx() }
    val hole = key?.let { targets[it] }?.translate(-origin)?.inflate(pad)
    // The composer is not laid out yet: nothing to point at, nothing stopped.
    if ((step == TourStep.SayHi || step == TourStep.Present) && hole == null) return

    Box(
        Modifier
            .fillMaxSize()
            .testTag(TOUR)
            .onGloballyPositioned {
                origin = it.positionInRoot()
                frame = it.size
            },
    ) {
        Shade(hole)
        // The last card stops nothing: pulling the forum menu or the sidebar ends it.
        if (step != TourStep.Finished) {
            Blockers(hole, frame, if (step == TourStep.Swipe) actions.onSwipe else null)
        }
        if (step == TourStep.Swipe) SwipingHand()
        Card(step, hole, frame) {
            CardText(step, tour, name, allLabel)
            if (step == TourStep.OpenTopic && hole == null && onboarding != null) {
                TextButton(onClick = actions.onOpenTopic) {
                    Text(stringResource(R.string.tour_open_topic_button, onboarding.topicTitle))
                }
            }
            if (step == TourStep.Finished) {
                Button(onClick = actions.onClose, Modifier.testTag(TOUR_CLOSE)) {
                    Text(stringResource(R.string.tour_close))
                }
            }
        }
        if (step == TourStep.Present && hole != null) LoneEnterHint(tour.loneEnters, hole)
    }
}

/** The page under its background colour at half opacity, a hole over [hole], glowing. */
@Composable
private fun Shade(hole: Rect?) {
    val scrim = MaterialTheme.colorScheme.background.copy(alpha = SCRIM_ALPHA)
    val glow = MaterialTheme.colorScheme.primary
    val pulse =
        if (IdejoTheme.reducedMotion) {
            1f
        } else {
            val pulsing by rememberInfiniteTransition(label = "tour glow").animateFloat(
                PULSE_LOW,
                1f,
                infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse),
                label = "tour glow",
            )
            pulsing
        }
    Canvas(Modifier.fillMaxSize()) {
        val corner = CornerRadius(HOLE_CORNER.toPx())
        val shade =
            Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                hole?.let { addRoundRect(RoundRect(it, corner)) }
            }
        drawPath(shade, scrim)
        hole ?: return@Canvas
        // The outline, then a halo fading outwards.
        for (ring in 0 until GLOW_RINGS) {
            val spread = ring * GLOW_STEP.toPx()
            drawRoundRect(
                glow.copy(alpha = (1f - ring / GLOW_RINGS.toFloat()) * pulse * GLOW_ALPHA),
                topLeft = Offset(hole.left - spread, hole.top - spread),
                size = Size(hole.width + 2 * spread, hole.height + 2 * spread),
                cornerRadius = CornerRadius(corner.x + spread),
                style = Stroke(width = GLOW_WIDTH.toPx()),
            )
        }
    }
}

/**
 * Touches outside [hole] go nowhere: four blocks around it (or one over everything), so the
 * hole's own touches reach what is under it. With [onSwipe], a drag to the right is the
 * sidebar's.
 */
@Composable
private fun Blockers(
    hole: Rect?,
    frame: IntSize,
    onSwipe: (() -> Unit)?,
) {
    val w = frame.width.toFloat()
    val h = frame.height.toFloat()
    val blocks =
        if (hole == null) {
            listOf(Rect(0f, 0f, w, h))
        } else {
            listOf(
                Rect(0f, 0f, w, hole.top),
                Rect(0f, hole.bottom, w, h),
                Rect(0f, hole.top, hole.left, hole.bottom),
                Rect(hole.right, hole.top, w, hole.bottom),
            )
        }
    val density = LocalDensity.current
    val swipe = with(density) { SWIPE.toPx() }
    blocks.filter { it.width > 0f && it.height > 0f }.forEach { block ->
        Box(
            Modifier
                .offset { IntOffset(block.left.roundToInt(), block.top.roundToInt()) }
                .size(with(density) { block.width.toDp() }, with(density) { block.height.toDp() })
                .pointerInput(onSwipe) { stopTouches(swipe, onSwipe) },
        )
    }
}

/** Every touch is taken; a drag right past [swipe] px calls [onSwipe], once. */
private suspend fun PointerInputScope.stopTouches(
    swipe: Float,
    onSwipe: (() -> Unit)?,
) = awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false).consume()
    var moved = Offset.Zero
    var swiped = false
    do {
        val event = awaitPointerEvent()
        event.changes.forEach {
            moved += it.positionChange()
            it.consume()
        }
        val rightwards = moved.x > swipe && moved.x > abs(moved.y)
        if (!swiped && onSwipe != null && rightwards) {
            swiped = true
            onSwipe()
        }
    } while (event.changes.any { it.pressed })
}

/** A hand swiping right across the middle, again and again (still, with reduced motion). */
@Composable
private fun SwipingHand() {
    val reduced = IdejoTheme.reducedMotion
    val travel = with(LocalDensity.current) { HAND_TRAVEL.toPx() }
    val shift =
        if (reduced) {
            0f
        } else {
            val moving by rememberInfiniteTransition(label = "hand").animateFloat(
                -1f,
                1f,
                infiniteRepeatable(tween(HAND_MILLIS, easing = LinearEasing), RepeatMode.Restart),
                label = "hand",
            )
            moving
        }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            painterResource(R.drawable.ic_swipe_hand),
            contentDescription = null,
            modifier =
                Modifier
                    .offset { IntOffset((shift * travel).roundToInt(), 0) }
                    .size(HAND_SIZE),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 1f - abs(shift) * HAND_FADE),
        )
    }
}

/**
 * The step's card: above [hole] (the composer), below it (in the sidebar), or in the middle
 * when there is none; an arrow on its edge points at the hole's middle.
 */
@Suppress("LongMethod") // The card, placed by its target, and its arrow.
@Composable
private fun Card(
    step: TourStep,
    hole: Rect?,
    frame: IntSize,
    content: @Composable () -> Unit,
) {
    var card by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val gap = with(density) { CARD_GAP.toPx() }
    val margin = with(density) { CARD_MARGIN.toPx() }
    val above = step == TourStep.SayHi || step == TourStep.Present
    val x =
        if (hole == null) {
            (frame.width - card.width) / 2f
        } else {
            (hole.center.x - card.width / 2f)
                .coerceIn(margin, (frame.width - card.width - margin).coerceAtLeast(margin))
        }
    val y =
        when {
            hole == null -> (frame.height - card.height) / 2f + with(density) { HAND_GAP.toPx() }
            above -> hole.top - gap - card.height
            else -> hole.bottom + gap
        }.coerceIn(margin, (frame.height - card.height - margin).coerceAtLeast(margin))
    val surface = MaterialTheme.colorScheme.surface
    val edge = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxSize()) {
        if (hole != null && card != IntSize.Zero) {
            // The arrow: a triangle from the card's edge to the hole.
            Canvas(Modifier.fillMaxSize()) {
                val tip = if (above) y + card.height + gap * ARROW_REACH else y - gap * ARROW_REACH
                val base = if (above) y + card.height - 1f else y + 1f
                val half = ARROW_HALF.toPx()
                val at = hole.center.x.coerceIn(x + half * 2, x + card.width - half * 2)
                val arrow =
                    Path().apply {
                        moveTo(at - half, base)
                        lineTo(at, tip)
                        lineTo(at + half, base)
                        close()
                    }
                drawPath(arrow, surface)
                drawPath(arrow, edge, style = Stroke(width = 1.dp.toPx()))
            }
        }
        Surface(
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .width(CARD_WIDTH)
                .onSizeChanged { card = it }
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(TOUR_CARD),
            shape = RoundedCornerShape(16.dp),
            color = surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, edge.copy(alpha = EDGE_ALPHA)),
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.Start,
            ) { content() }
        }
    }
}

/** The step's words; a key's name drawn as a key. */
@Composable
private fun CardText(
    step: TourStep,
    tour: OnboardingTour.State,
    name: String,
    allLabel: String,
) {
    val enter = key(stringResource(R.string.tour_key_enter))
    val title = tour.onboarding?.topicTitle.orEmpty()
    val text =
        when (step) {
            TourStep.Swipe -> {
                stringResource(R.string.tour_swipe)
            }

            TourStep.OpenTopic -> {
                stringResource(R.string.tour_open_topic, title)
            }

            TourStep.SayHi -> {
                stringResource(R.string.tour_say_hi, name, enter)
            }

            TourStep.Present -> {
                stringResource(R.string.tour_present, tour.onboarding?.question.orEmpty(), enter)
            }

            TourStep.Finished -> {
                stringResource(R.string.tour_finished, allLabel)
            }
        }
    Text(withKeys(text), style = IdejoTheme.typography.bodyLarge)
}

/**
 * "Hit Enter three times in quick succession" (IX-18), over the card, each time an Enter
 * came alone ([loneEnters] grew), fading away after 2 seconds.
 */
@Composable
private fun LoneEnterHint(
    loneEnters: Int,
    hole: Rect,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(loneEnters) {
        if (loneEnters == 0) return@LaunchedEffect
        shown = true
        delay(HINT_MILLIS)
        shown = false
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // Astride the composer's top edge, in its middle.
    Box(
        Modifier
            .offset {
                IntOffset(
                    (hole.center.x - size.width / 2f).roundToInt(),
                    (hole.top - size.height / 2f).roundToInt(),
                )
            }.onSizeChanged { size = it },
    ) {
        AnimatedVisibility(shown, enter = fadeIn(), exit = fadeOut()) {
            Surface(
                Modifier.testTag(TOUR_HINT),
                shape = RoundedCornerShape(HINT_CORNER),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 4.dp,
            ) {
                Text(
                    withKeys(
                        stringResource(
                            R.string.tour_three_enters,
                            key(stringResource(R.string.tour_key_enter)),
                        ),
                    ),
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = IdejoTheme.typography.labelLarge,
                )
            }
        }
    }
}

internal const val TOUR = "tour"
internal const val TOUR_CARD = "tour-card"
internal const val TOUR_CLOSE = "tour-close"
internal const val TOUR_HINT = "tour-hint"

private const val SCRIM_ALPHA = 0.5f
private const val GLOW_RINGS = 4
private const val GLOW_ALPHA = 0.8f
private const val PULSE_LOW = 0.55f
private const val PULSE_MILLIS = 1_600
private const val HAND_MILLIS = 1_400
private const val HAND_FADE = 0.6f
private const val HINT_MILLIS = 2_000L
private const val ARROW_REACH = 0.85f
private const val EDGE_ALPHA = 0.55f
private val HOLE_PAD = 4.dp
private val HOLE_CORNER = 12.dp
private val GLOW_STEP = 3.dp
private val GLOW_WIDTH = 2.dp
private val SWIPE = 48.dp
private val HAND_SIZE = 56.dp
private val HAND_TRAVEL = 64.dp
private val HAND_GAP = 72.dp
private val CARD_WIDTH = 300.dp
private val CARD_GAP = 14.dp
private val CARD_MARGIN = 12.dp
private val ARROW_HALF = 8.dp
private val HINT_CORNER = 16.dp
