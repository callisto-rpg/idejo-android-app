// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A mouse's right-click does what a long-press does (SH-62): [onClick] runs on a press of
 * the secondary button. Nothing when [onClick] is null.
 */
fun Modifier.onSecondaryClick(onClick: (() -> Unit)?): Modifier =
    if (onClick == null) {
        this
    } else {
        pointerInput(onClick) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                        event.changes.forEach { it.consume() }
                        onClick()
                    }
                }
            }
        }
    }

/**
 * Visible focus (SH-62): what [ripple] draws, and, while focused from a keyboard, a ring of
 * [colour] around the element. The theme's indication, so every clickable has it.
 */
class FocusRing(
    private val ripple: IndicationNodeFactory,
    private val colour: Color,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        FocusRingNode(interactionSource, ripple.create(interactionSource), colour)

    override fun equals(other: Any?): Boolean =
        other is FocusRing && other.ripple == ripple && other.colour == colour

    override fun hashCode(): Int = 31 * ripple.hashCode() + colour.hashCode()
}

private class FocusRingNode(
    private val source: InteractionSource,
    ripple: DelegatableNode,
    private val colour: Color,
) : DelegatingNode(),
    DrawModifierNode {
    private var focused = false

    init {
        delegate(ripple)
    }

    override fun onAttach() {
        coroutineScope.launch {
            var count = 0
            source.interactions.collect {
                when (it) {
                    is FocusInteraction.Focus -> count++
                    is FocusInteraction.Unfocus -> count--
                }
                val now = count > 0
                if (now != focused) {
                    focused = now
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!focused) return
        val width = RING.toPx()
        drawRoundRect(
            colour,
            cornerRadius = CornerRadius(CORNER.toPx()),
            style = Stroke(width),
        )
    }
}

private val RING = 2.dp
private val CORNER = 8.dp
