// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.idejo.app.core.designsystem.R
import org.idejo.app.core.designsystem.theme.BubbleKind
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.designsystem.theme.mix

/**
 * An in-app bubble (NT-01): what happened ([title]) and an excerpt ([text]), tinted by its
 * kind (grey followed, blue role mention, orange reply, red mention, purple reaction).
 * Tapping it goes to the message; × dismisses it, so a swipe is never needed (AX-04).
 */
@Composable
fun Bubble(
    kind: BubbleKind,
    title: String,
    text: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = IdejoTheme.colors.bubble(kind)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = mix(MaterialTheme.colorScheme.surface, accent, TINT),
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ColorDot(accent, Modifier.padding(top = 2.dp), size = 10.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = IdejoTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text,
                    style = IdejoTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.bubble_dismiss))
            }
        }
    }
}

/** How a [StatusBubble] looks: the forum's primary, or slow mode's orange (CO-05, CO-07). */
enum class StatusTone { Primary, SlowMode }

/** The small bubble beside the composer's picture: "sent", "slow mode"… (CO-05, CO-07). */
@Composable
fun StatusBubble(
    text: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Primary,
) {
    val (container, content) =
        when (tone) {
            StatusTone.Primary -> {
                MaterialTheme.colorScheme.primary to
                    MaterialTheme.colorScheme.onPrimary
            }

            StatusTone.SlowMode -> {
                IdejoTheme.colors.slowModeContainer to IdejoTheme.colors.onSlowModeContainer
            }
        }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        color = container,
        contentColor = content,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = IdejoTheme.typography.labelSmall,
        )
    }
}

private const val TINT = 0.12f
