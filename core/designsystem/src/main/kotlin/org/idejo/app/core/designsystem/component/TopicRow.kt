// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.idejo.app.core.designsystem.R
import org.idejo.app.core.designsystem.theme.DotKind
import org.idejo.app.core.designsystem.theme.IdejoTheme

/** One of a topic's latest messages in the Expanded view (BR-03): two lines at most. */
@Immutable
data class LatestMessage(
    val author: String,
    val text: String,
)

/** Someone who wrote in a topic, for the Expanded view's pictures (BR-03). */
@Immutable
data class Writer(
    val name: String,
    val color: Color,
)

/**
 * What the Expanded view adds to a topic (BR-03): the start of its first message, its three
 * latest messages and who wrote there (five pictures, then "+N more").
 */
@Immutable
data class TopicExpansion(
    val excerpt: String,
    val latest: List<LatestMessage> = emptyList(),
    val writers: List<Writer> = emptyList(),
)

/**
 * A topic in a list (BR-02, BR-03): its dot (SH-32), title, who started it and when it was
 * last active, its tags at the right above its number of messages. Compact by default;
 * with [expansion], the Expanded view.
 */
@Composable
fun TopicRow(
    title: String,
    starter: String,
    lastActivity: String,
    messagesCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tags: List<String> = emptyList(),
    dot: DotKind? = null,
    pinned: Boolean = false,
    expansion: TopicExpansion? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (dot != null) Dot(dot, Modifier.padding(top = 6.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = IdejoTheme.typography.titleMedium,
                    maxLines = if (expansion == null) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Meta(starter, lastActivity, pinned)
                if (expansion != null) Expansion(expansion)
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tags.forEach { Tag(it) }
                Text(
                    pluralStringResource(R.plurals.topic_messages, messagesCount, messagesCount),
                    style = IdejoTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Meta(
    starter: String,
    lastActivity: String,
    pinned: Boolean,
) {
    val meta = stringResource(R.string.topic_meta, starter, lastActivity)
    Text(
        if (pinned) {
            stringResource(
                R.string.topic_meta,
                stringResource(R.string.topic_pinned),
                meta,
            )
        } else {
            meta
        },
        style = IdejoTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Expansion(expansion: TopicExpansion) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            expansion.excerpt,
            style = IdejoTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        expansion.latest.take(LATEST).forEach { message ->
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(message.author) }
                    append(": ")
                    append(message.text)
                },
                style = IdejoTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (expansion.writers.isNotEmpty()) Writers(expansion.writers)
    }
}

@Composable
private fun Writers(writers: List<Writer>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Overlapping a little, as a group.
        Row(horizontalArrangement = Arrangement.spacedBy(-WRITER_OVERLAP)) {
            writers.take(WRITERS).forEach { Avatar(it.name, it.color, size = 24.dp) }
        }
        val more = writers.size - WRITERS
        if (more > 0) {
            Text(
                pluralStringResource(R.plurals.topic_more_writers, more, more),
                Modifier.padding(start = 6.dp),
                style = IdejoTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Tag(name: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            name,
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = IdejoTheme.typography.labelMedium,
        )
    }
}

private const val LATEST = 3
private const val WRITERS = 5
private val WRITER_OVERLAP = 6.dp
