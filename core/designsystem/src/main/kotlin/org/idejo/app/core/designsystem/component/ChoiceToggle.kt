// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.idejo.app.core.designsystem.theme.IdejoTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The app's one toggle selector (steward request 2026-11-23), as the web's view toggle in the
 * forum's colours: a rectangle with rounded corners holding the options side by side, the
 * chosen one under a selector in the primary colour, no check-marks. A tap picks an option
 * and the selector slides there; the selector can also be dragged from side to side, and the
 * option it rests nearest when let go is picked. Reduced motion moves it at once.
 *
 * [fill] spreads the options over the width given; otherwise each is as wide as the widest
 * label. TalkBack reads each option as a tab, selected or not.
 */
@Composable
fun <T> ChoiceToggle(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    label: @Composable (T) -> String,
) {
    val index = options.indexOf(selected).coerceAtLeast(0)
    val slide = rememberSlide(index, options.size) { onSelect(options[it]) }
    val colors = MaterialTheme.colorScheme
    Layout(
        content = {
            Box(
                Modifier
                    .layoutId(INDICATOR)
                    .clip(RoundedCornerShape(INNER_RADIUS))
                    .background(colors.primary),
            )
            options.forEachIndexed { i, option ->
                ToggleOption(label(option), i == index, slide.nearness(i)) { onSelect(option) }
            }
        },
        modifier =
            modifier
                .height(40.dp)
                .clip(RoundedCornerShape(OUTER_RADIUS))
                .background(colors.surfaceContainerHigh)
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(OUTER_RADIUS))
                .selectableGroup()
                .draggable(
                    slide.drag,
                    Orientation.Horizontal,
                    enabled = options.size > 1,
                    onDragStarted = { slide.dragging = true },
                    onDragStopped = { slide.settle() },
                ),
        measurePolicy = toggleMeasure(slide, fill),
    )
}

/** One option: its label, turning from the surface's text colour to the selector's. */
@Composable
private fun ToggleOption(
    text: String,
    selected: Boolean,
    nearness: Float,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(INNER_RADIUS))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = lerp(colors.onSurface, colors.onPrimary, nearness),
            style = IdejoTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Where the selector stands, in options (0 the first), while it slides or is dragged. */
private class Slide(
    private val count: Int,
    private val rtl: Boolean,
    private val pick: (Int) -> Unit,
) {
    var position by mutableFloatStateOf(0f)
    var dragging by mutableStateOf(false)
    var index = 0

    /** The width of one option, in pixels, as last laid out. */
    var segment = 1f

    val drag =
        DraggableState { delta ->
            val moved = (if (rtl) -delta else delta) / segment
            position = (position + moved).coerceIn(0f, (count - 1).toFloat())
        }

    fun nearness(i: Int): Float = (1f - abs(position - i)).coerceIn(0f, 1f)

    fun settle() {
        val nearest = position.roundToInt().coerceIn(0, count - 1)
        dragging = false
        if (nearest != index) pick(nearest)
    }
}

@Composable
private fun rememberSlide(
    index: Int,
    count: Int,
    pick: (Int) -> Unit,
): Slide {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentPick by rememberUpdatedState(pick)
    val slide =
        remember(count, rtl) {
            Slide(count, rtl) { currentPick(it) }.also {
                it.position =
                    index.toFloat()
            }
        }
    slide.index = index
    val reduced = IdejoTheme.reducedMotion
    LaunchedEffect(slide, index, slide.dragging, reduced) {
        if (slide.dragging) return@LaunchedEffect
        if (reduced) {
            slide.position = index.toFloat()
        } else {
            val spec = spring<Float>(stiffness = Spring.StiffnessMediumLow)
            animate(slide.position, index.toFloat(), animationSpec = spec) { value, _ ->
                slide.position =
                    value
            }
        }
    }
    return slide
}

/** The options side by side in equal widths inside the border; the selector at [Slide.position]. */
private fun toggleMeasure(
    slide: Slide,
    fill: Boolean,
) = MeasurePolicy { measurables, constraints ->
    val inset = INSET.roundToPx()
    val indicator = measurables.first()
    val items = measurables.drop(1)
    val count = items.size.coerceAtLeast(1)
    val height = constraints.maxHeight
    val natural = items.maxOfOrNull { it.maxIntrinsicWidth(height) } ?: 0
    val wanted =
        if (fill &&
            constraints.hasBoundedWidth
        ) {
            constraints.maxWidth
        } else {
            natural * count + 2 * inset
        }
    val width = wanted.coerceIn(constraints.minWidth, constraints.maxWidth)
    val each = ((width - 2 * inset) / count).coerceAtLeast(0)
    slide.segment = each.toFloat().coerceAtLeast(1f)
    val inner = Constraints.fixed(each, (height - 2 * inset).coerceAtLeast(0))
    val placedIndicator = indicator.measure(inner)
    val placedItems = items.map { it.measure(inner) }
    layout(width, height) {
        placedIndicator.placeRelative((inset + slide.position * each).roundToInt(), inset)
        placedItems.forEachIndexed { i, item -> item.placeRelative(inset + i * each, inset) }
    }
}

private const val INDICATOR = "indicator"
private val OUTER_RADIUS = 10.dp
private val INNER_RADIUS = 7.dp
private val INSET = 3.dp
