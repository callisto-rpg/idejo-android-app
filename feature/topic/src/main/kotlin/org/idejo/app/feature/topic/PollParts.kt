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

/** The answer's emoji, when the forum knows its picture or character. */
@Composable
internal fun OptionEmoji(
    option: PollOption,
    context: MessageContext,
) {
    val name = option.emoji ?: return
    val emoji = context.emoji.firstOrNull { it.name == name } ?: return
    val char = emoji.char
    val image = emoji.imageUrl
    when {
        char != null -> Text(char, style = IdejoTheme.typography.titleMedium)
        image != null -> AsyncImage(absolute(context.origin, image), null, Modifier.size(22.dp))
    }
}

/** "Choose any number of answers.", the voters, when it closes, when the results show. */
@Composable
internal fun Meta(poll: Poll) {
    val parts =
        buildList {
            if (poll.multiple) add(stringResource(R.string.poll_any_number))
            poll.total?.let { add(pluralStringResource(R.plurals.poll_voters, it, it)) }
            val closes = poll.closesAt
            when {
                poll.closed -> add(stringResource(R.string.poll_closed))
                closes != null -> add(stringResource(R.string.poll_closes, CLOSING.format(closes)))
            }
            if (!poll.closed && !poll.resultsShown) {
                add(
                    stringResource(
                        if (poll.hiddenUntilClosed) {
                            R.string.poll_results_at_close
                        } else {
                            R.string.poll_results_after_vote
                        },
                    ),
                )
            }
        }
    Text(
        parts.joinToString("  ·  "),
        style = IdejoTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val CLOSING =
    DateTimeFormatter
        .ofLocalizedDateTime(
            FormatStyle.MEDIUM,
            FormatStyle.SHORT,
        ).withZone(ZoneId.systemDefault())
