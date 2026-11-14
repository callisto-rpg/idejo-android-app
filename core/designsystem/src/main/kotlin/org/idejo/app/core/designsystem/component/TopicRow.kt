// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
    /** Their picture's address, drawn by the row's `picture`. */
    val picture: String? = null,
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
    /** The topic's uploaded pictures' addresses: four tiles, then "+N images" (BR-03). */
    val images: List<String> = emptyList(),
    val imagesCount: Int = images.size,
    /** Everyone who wrote, when more than [writers] holds. */
    val writersCount: Int = writers.size,
)

/**
 * A topic in a list (BR-02, BR-03): its dot (SH-32), [lead] (the starter's picture, or the
 * category's mark on the home page), title, who started it (or its category) and when it was
 * last active, its tags at the right above "N new" ([unread]) and its number of messages.
 * Compact by default; with [expansion], the Expanded view, whose pictures [picture] draws.
 * An [inactive] topic is faded, as the web's.
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
    unread: Int = 0,
    inactive: Boolean = false,
    lead: (@Composable () -> Unit)? = null,
    picture: (@Composable (url: String, modifier: Modifier) -> Unit)? = null,
) {
    val inactiveWords = stringResource(R.string.topic_inactive)
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (inactive) {
                        Modifier.alpha(INACTIVE).semantics { stateDescription = inactiveWords }
                    } else {
                        Modifier
                    },
                ),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (dot != null) Dot(dot, Modifier.padding(top = 6.dp))
            lead?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = IdejoTheme.typography.titleMedium,
                    maxLines = if (expansion == null) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Meta(starter, lastActivity, pinned)
                if (expansion != null) Expansion(expansion, picture)
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                tags.forEach { Tag(it) }
                if (unread > 0) NewBadge(unread)
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
private fun Expansion(
    expansion: TopicExpansion,
    picture: (@Composable (url: String, modifier: Modifier) -> Unit)?,
) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                expansion.excerpt,
                Modifier.weight(1f),
                style = IdejoTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (picture != null && expansion.images.isNotEmpty()) {
                Images(expansion.images, expansion.imagesCount, picture)
            }
        }
        if (expansion.latest.isNotEmpty()) {
            Text(
                stringResource(R.string.topic_latest),
                style = IdejoTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        if (expansion.writers.isNotEmpty()) {
            Writers(expansion.writers, expansion.writersCount, picture)
        }
    }
}

/** Up to four of the topic's pictures, two by two; "+N" over the last when there are more. */
@Composable
private fun Images(
    images: List<String>,
    count: Int,
    picture: @Composable (url: String, modifier: Modifier) -> Unit,
) {
    val shown = images.take(TILES)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        shown.chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                pair.forEachIndexed { column, url ->
                    val tile = Modifier.size(TILE).clip(RoundedCornerShape(4.dp))
                    Box(tile, contentAlignment = Alignment.Center) {
                        picture(url, Modifier.matchParentSize())
                        val last = row * 2 + column == shown.lastIndex
                        if (last && count > shown.size) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .background(
                                        MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM),
                                    ),
                            )
                            Text(
                                pluralStringResource(
                                    R.plurals.topic_more_images,
                                    count - shown.size,
                                    count - shown.size,
                                ),
                                style = IdejoTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewBadge(count: Int) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Text(
            pluralStringResource(R.plurals.topic_new, count, count),
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = IdejoTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun Writers(
    writers: List<Writer>,
    count: Int,
    picture: (@Composable (url: String, modifier: Modifier) -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Overlapping a little, as a group.
        Row(horizontalArrangement = Arrangement.spacedBy(-WRITER_OVERLAP)) {
            writers.take(WRITERS).forEach { writer ->
                Avatar(
                    writer.name,
                    writer.color,
                    size = 24.dp,
                    image =
                        if (picture != null && writer.picture != null) {
                            { picture(writer.picture, Modifier.fillMaxSize()) }
                        } else {
                            null
                        },
                )
            }
        }
        val more = maxOf(count, writers.size) - WRITERS
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
private const val TILES = 4
private const val INACTIVE = 0.5f
private const val SCRIM = 0.45f
private val TILE = 34.dp
private val WRITER_OVERLAP = 6.dp
