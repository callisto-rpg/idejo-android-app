// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.idejo.app.core.data.TutorialBubbles
import org.idejo.app.core.designsystem.component.StatusBubble
import org.idejo.app.core.designsystem.component.StatusTone
import org.idejo.app.core.designsystem.theme.IdejoTheme

/**
 * Slow mode (CO-07): the thin orange line fills under the box with the **slow mode** bubble
 * and the web's rotating hints; at its end it flashes and the bubble says how to start a new
 * message. Hidden from screen readers, as the web's (the box's description says it).
 */
@Composable
internal fun SlowModeLine(
    line: TimerLine,
    bubbles: TutorialBubbles,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(line) {
        while (!line.done(now)) {
            delay(TICK_MILLIS)
            now = System.currentTimeMillis()
        }
    }
    val done = line.done(now)
    val flash = remember { Animatable(1f) }
    val reduced = IdejoTheme.reducedMotion
    LaunchedEffect(done) {
        if (done && !reduced) {
            flash.animateTo(0f, repeatable(FLASHES, tween(FLASH_MILLIS), RepeatMode.Reverse))
            flash.snapTo(1f)
        }
    }
    Column(
        modifier.fillMaxWidth().clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(3.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth(line.progress(now))
                        .height(3.dp)
                        .alpha(flash.value)
                        .background(IdejoTheme.colors.slowMode),
                )
            }
            if (bubbles == TutorialBubbles.All) {
                StatusBubble(
                    if (done) startNewLabel() else stringResource(R.string.composer_slow_mode),
                    tone = StatusTone.SlowMode,
                )
            }
        }
        if (bubbles == TutorialBubbles.All && !done) Hint(line.held)
    }
}

/** "Ctrl+Enter to start new message", or on touch "Enter 3× to start new message". */
@Composable
private fun startNewLabel(): String {
    val keyboard = LocalConfiguration.current.keyboard == Configuration.KEYBOARD_QWERTY
    return stringResource(
        if (keyboard) R.string.composer_start_new_keys else R.string.composer_start_new_touch,
    )
}

/** The web's hints under the line, each for 3 s in turn. */
@Composable
private fun Hint(held: Boolean) {
    val hints = stringArrayResource(R.array.composer_slow_hints)
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(HINT_MILLIS)
            index = (index + 1) % hints.size
        }
    }
    Text(
        if (held) stringResource(R.string.composer_held_hint) else hints[index],
        Modifier.fillMaxWidth(),
        style = IdejoTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

private const val TICK_MILLIS = 100L
private const val HINT_MILLIS = 3_000L
private const val FLASH_MILLIS = 150
private const val FLASHES = 5
