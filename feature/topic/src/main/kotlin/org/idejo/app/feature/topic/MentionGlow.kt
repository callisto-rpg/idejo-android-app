// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.idejo.app.core.data.Glow
import org.idejo.app.core.data.namesMember
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.markdown.MentionPulse
import org.idejo.app.core.model.Message

/**
 * A message naming the member, as it glows once reached (TV-07), as the web's: in its dot's
 * colour, medium, bigger in 0.3 s, held 1 s, faded in 3 s; the tags naming the member pulse
 * three times, 0.5 s each. With reduced motion it neither grows nor pulses, only fades.
 */
@Composable
internal fun GlowingMessage(
    message: Message,
    context: MessageContext,
    glow: Glow,
    onDone: () -> Unit,
) {
    val colour =
        when (glow) {
            Glow.Mention -> IdejoTheme.colors.dotMention
            Glow.Role -> IdejoTheme.colors.dotRoleMention
        }
    val reduced = IdejoTheme.reducedMotion
    val strength = remember { Animatable(if (reduced) 1f else MEDIUM) }
    val pulse = remember { Animatable(0f) }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(message.id) {
        if (!reduced) {
            launch {
                repeat(PULSES) {
                    pulse.animateTo(1f, tween(HALF_PULSE_MS))
                    pulse.animateTo(0f, tween(HALF_PULSE_MS))
                }
            }
            strength.animateTo(1f, tween(RISE_MS))
        }
        delay(HOLD_MS)
        strength.animateTo(0f, tween(FADE_MS))
        done()
    }
    val me = context.me
    MessageItem(
        message,
        context,
        Modifier.glow(colour) { strength.value },
        pulse = me?.let { MentionPulse({ it.namesMember(me) }, colour) { pulse.value } },
    )
}

/**
 * The messages glowing now (TV-07): each of [glows] starts once per page, when reached
 * ([WatchGlows]), and leaves the map when its glow ends.
 */
@Composable
internal fun rememberGlowing(
    list: LazyListState,
    glows: Map<String, Glow>,
): SnapshotStateMap<String, Glow> {
    var started by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val glowing = remember { mutableStateMapOf<String, Glow>() }
    WatchGlows(list, glows, started) { id ->
        started = started + id
        glows[id]?.let { glowing[id] = it }
    }
    return glowing
}

/**
 * Starts each message of [glows] glowing once it is reached: half of it on screen, or, taller
 * than the screen, a third of the screen (the web's rule). [start] is told once per message.
 */
@Composable
private fun WatchGlows(
    list: LazyListState,
    glows: Map<String, Glow>,
    started: Set<String>,
    start: (String) -> Unit,
) {
    val pending by rememberUpdatedState(glows.keys - started)
    val onSeen by rememberUpdatedState(start)
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo }.collect { info ->
            val view = info.viewportEndOffset - info.viewportStartOffset
            info.visibleItemsInfo
                .filter {
                    it.key in pending &&
                        it.seen(info.viewportStartOffset, info.viewportEndOffset, view)
                }.forEach { onSeen(it.key as String) }
        }
    }
}

private fun LazyListItemInfo.seen(
    top: Int,
    bottom: Int,
    view: Int,
): Boolean {
    val shown = minOf(offset + size, bottom) - maxOf(offset, top)
    return shown > 0 && (shown * HALF >= size || shown * THIRD >= view)
}

/** The glow around a message, drawn from [strength] without recomposing. */
internal fun Modifier.glow(
    colour: Color,
    strength: () -> Float,
) = drawBehind {
    val s = strength()
    if (s <= 0f) return@drawBehind
    val inset = INSET.dp.toPx()
    val corner = CORNER.dp.toPx()
    val ring = RING.dp.toPx()
    val box = Size(size.width - 2 * inset, size.height - 2 * inset)
    // A soft halo: rings further out, fainter, wider as it grows.
    for (i in HALO downTo 1) {
        val out = i * ring * s
        drawRoundRect(
            colour.copy(alpha = HALO_ALPHA * s / i),
            topLeft = Offset(inset - out, inset - out),
            size = Size(box.width + 2 * out, box.height + 2 * out),
            cornerRadius = CornerRadius(corner + out),
            style = Stroke(ring * s),
        )
    }
    drawRoundRect(
        colour.copy(alpha = LINE_ALPHA * s),
        topLeft = Offset(inset, inset),
        size = box,
        cornerRadius = CornerRadius(corner),
        style = Stroke(LINE.dp.toPx() * (1f + s) / 2f),
    )
}

private const val HALF = 2
private const val THIRD = 3
private const val MEDIUM = 0.6f
private const val RISE_MS = 300
private const val HOLD_MS = 1000L
private const val FADE_MS = 3000
private const val PULSES = 3
private const val HALF_PULSE_MS = 250
private const val INSET = 6
private const val CORNER = 12
private const val RING = 3
private const val HALO = 4
private const val HALO_ALPHA = 0.35f
private const val LINE = 2
private const val LINE_ALPHA = 0.85f
