// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import org.idejo.app.core.data.TutorialBubbles
import java.time.Instant
import org.idejo.app.core.model.ComposerState as ServerComposer

/** The thin orange line under the box (CO-07), on the device's clock. */
data class TimerLine(
    val startMillis: Long,
    val endMillis: Long,
    /** The author's last message closed early: the next one waits for the line. */
    val held: Boolean = false,
) {
    fun progress(nowMillis: Long): Float =
        if (endMillis <= startMillis) {
            1f
        } else {
            ((nowMillis - startMillis).toFloat() / (endMillis - startMillis)).coerceIn(0f, 1f)
        }

    fun done(nowMillis: Long): Boolean = nowMillis >= endMillis
}

/**
 * The line from the server's state, never from the rules (CO-07): it ends at
 * `may_start_new_at`, at `held_until` after a message closed early, and before the first
 * send at `typing_since` plus the window, as the web draws the draft's. The server's times
 * move to the device's clock by the offset to its `now`, read when the state came
 * ([receivedAtMillis]). No line without slow mode (private conversations).
 */
fun timerLine(
    server: ServerComposer?,
    openedAt: Instant?,
    receivedAtMillis: Long,
): TimerLine? {
    val now = server?.now ?: return null
    if (server.windowSeconds <= 0 && server.unansweredSeconds <= 0) return null
    val window = server.windowSeconds.toLong()
    val typing = server.typingSince
    val held = server.heldUntil
    val open = server.mayStartNewAt.takeIf { server.openMessageId != null }
    val (start, end) =
        when {
            open != null -> (openedAt ?: typing ?: open.minusSeconds(window)) to open
            held != null -> now to held
            typing != null -> typing to typing.plusSeconds(window)
            else -> return null
        }
    val offset = receivedAtMillis - now.toEpochMilli()
    return TimerLine(
        start.toEpochMilli() + offset,
        end.toEpochMilli() + offset,
        held = open == null && held != null,
    )
}

/** The tutorial bubble beside the caret (CO-06). */
enum class TutorialTip { EnterToSend, Sent, Continue }

/**
 * Which tip shows: **Enter to send** while something is not sent, **Sent!** for a moment
 * after a send ([justSent]), then **Continue typing to add to your message** until the
 * author types. Simple keeps only the first two; Off none.
 */
fun tutorialTip(
    state: ComposerState,
    justSent: Boolean,
    bubbles: TutorialBubbles,
): TutorialTip? =
    when {
        bubbles == TutorialBubbles.Off -> null
        state.hasUnsent && state.text.isNotBlank() -> TutorialTip.EnterToSend
        justSent && state.sent.isNotEmpty() -> TutorialTip.Sent
        bubbles == TutorialBubbles.All && state.sent.isNotEmpty() -> TutorialTip.Continue
        else -> null
    }
