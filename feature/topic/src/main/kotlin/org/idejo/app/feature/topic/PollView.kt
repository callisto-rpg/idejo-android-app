// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.designsystem.theme.parseHexColor
import org.idejo.app.core.model.Poll
import org.idejo.app.core.model.PollOption
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A poll under its message (TV-27), as the web's: the answers side by side, each with its bar,
 * count, emoji and text; when they do not fit, the bars stay side by side and the answers go
 * in rows below. **Vote** / **Change vote** sends the choice; the results show as the server
 * allows (after voting, or once it closes).
 */
@Composable
internal fun PollView(
    messageId: String,
    poll: Poll,
    context: MessageContext,
) {
    var chosen by rememberSaveable(poll.id, poll.myVotes) { mutableStateOf(poll.myVotes) }
    val voting = context.mayVote && !poll.closed
    val choose: (String) -> Unit = { id ->
        chosen =
            when {
                !poll.multiple -> listOf(id)
                id in chosen -> chosen - id
                else -> chosen + id
            }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            poll.question,
            Modifier.semantics { heading() },
            style = IdejoTheme.typography.titleSmall,
        )
        BoxWithConstraints {
            val side = maxWidth / poll.options.size.coerceAtLeast(1) >= COLUMN_MIN
            if (side) {
                Columns(poll, chosen, voting, context, choose)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (poll.resultsShown) Bars(poll, context, Modifier.fillMaxWidth())
                    Rows(poll, chosen, voting, context, choose)
                }
            }
        }
        Meta(poll)
        if (voting) {
            val sending = poll.id in context.voting
            OutlinedButton(
                onClick = { context.onVote(messageId, poll.id, chosen) },
                enabled = !sending && (chosen.isNotEmpty() || poll.myVotes.isNotEmpty()),
            ) {
                if (sending) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        stringResource(
                            if (poll.myVotes.isEmpty()) {
                                R.string.poll_vote
                            } else {
                                R.string.poll_change_vote
                            },
                        ),
                    )
                }
            }
        }
    }
}

/** Each answer a column: bar, count, emoji, then its choice and text. */
@Composable
private fun Columns(
    poll: Poll,
    chosen: List<String>,
    voting: Boolean,
    context: MessageContext,
    choose: (String) -> Unit,
) = Row(
    Modifier.fillMaxWidth().selectableGroup(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    poll.options.forEach { option ->
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (poll.resultsShown) Bar(poll, option, Modifier.width(BAR_WIDTH))
            if (poll.resultsShown) Count(poll, option)
            OptionEmoji(option, context)
            Choice(poll, option, chosen, voting, choose, centred = true)
        }
    }
}

/** The bars, counts and emojis side by side, above the answers in rows. */
@Composable
private fun Bars(
    poll: Poll,
    context: MessageContext,
    modifier: Modifier = Modifier,
) = Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    poll.options.forEach { option ->
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Bar(poll, option, Modifier.width(BAR_WIDTH))
            Count(poll, option)
            OptionEmoji(option, context)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Rows(
    poll: Poll,
    chosen: List<String>,
    voting: Boolean,
    context: MessageContext,
    choose: (String) -> Unit,
) = Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    poll.options.forEach { option ->
        FlowRow(verticalArrangement = Arrangement.Center) {
            if (!poll.resultsShown) OptionEmoji(option, context)
            Choice(poll, option, chosen, voting, choose, centred = false)
        }
    }
}

/** An answer's radio button or checkbox and its text, the whole row the target (AX). */
@Composable
private fun Choice(
    poll: Poll,
    option: PollOption,
    chosen: List<String>,
    voting: Boolean,
    choose: (String) -> Unit,
    centred: Boolean,
) {
    val on = option.id in chosen
    val target =
        if (poll.multiple) {
            Modifier.toggleable(on, enabled = voting, role = Role.Checkbox) { choose(option.id) }
        } else {
            Modifier.selectable(on, enabled = voting, role = Role.RadioButton) { choose(option.id) }
        }
    val text = @Composable {
        Text(
            option.text,
            style = IdejoTheme.typography.bodyMedium,
            textAlign = if (centred) TextAlign.Center else TextAlign.Start,
        )
    }
    if (centred) {
        Column(target, horizontalAlignment = Alignment.CenterHorizontally) {
            Mark(poll, on, voting)
            text()
        }
    } else {
        Row(target.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Mark(poll, on, voting)
            text()
        }
    }
}

@Composable
private fun Mark(
    poll: Poll,
    on: Boolean,
    voting: Boolean,
) = if (poll.multiple) {
    Checkbox(on, onCheckedChange = null, enabled = voting)
} else {
    RadioButton(on, onClick = null, enabled = voting)
}

/** A bar as tall as the answer's share of the voters, in its colour or the theme's. */
@Composable
private fun Bar(
    poll: Poll,
    option: PollOption,
    modifier: Modifier = Modifier,
) {
    val share = share(option.count, poll.total)
    val fill = parseHexColor(option.colour) ?: MaterialTheme.colorScheme.primary
    Box(
        modifier
            .height(BAR_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp))
            .clearAndSetSemantics {},
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (share > 0f) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(share)
                    .background(fill, RoundedCornerShape(4.dp)),
            )
        }
    }
}

/** "N votes" and its share, as the web's. */
@Composable
private fun Count(
    poll: Poll,
    option: PollOption,
) {
    val count = option.count ?: 0
    Text(
        pluralStringResource(R.plurals.poll_votes, count, count) + " " +
            stringResource(R.string.poll_percent, (share(count, poll.total) * PERCENT).toInt()),
        style = IdejoTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )
}

private fun share(
    count: Int?,
    total: Int?,
): Float = if (count == null || total == null || total <= 0) 0f else count.toFloat() / total

private val COLUMN_MIN: Dp = 88.dp
private val BAR_WIDTH: Dp = 28.dp
private val BAR_HEIGHT: Dp = 96.dp
private const val PERCENT = 100
