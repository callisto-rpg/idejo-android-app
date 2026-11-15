// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.component

import android.text.format.DateUtils
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.designsystem.theme.parseHexColor
import org.idejo.app.core.designsystem.theme.pictureUrl
import org.idejo.app.core.model.Category
import org.idejo.app.core.model.Person
import org.idejo.app.core.model.Tag
import org.idejo.app.core.model.Topic
import java.time.Instant

/**
 * A [Topic] as a row of a list (BR-02, BR-03): led by its starter's picture, or with
 * [category] (the home page's active topics) by the category's mark and name. [expanded]
 * shows its preview when the list was read with one. [picture] draws an address; [onTag]
 * opens a tag tapped (BR-04).
 */
@Composable
fun TopicItem(
    topic: Topic,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    category: Category? = null,
    picture: (@Composable (url: String, modifier: Modifier) -> Unit)? = null,
    onTag: ((Tag) -> Unit)? = null,
) {
    val dark = IdejoTheme.dark
    val scheme = MaterialTheme.colorScheme
    val author = topic.author
    TopicRow(
        title = topic.title,
        starter = category?.title ?: author?.displayName.orEmpty(),
        lastActivity = relativeTime(topic.lastActivityAt ?: topic.createdAt),
        messagesCount = topic.messagesCount,
        onClick = onClick,
        modifier = modifier,
        tags = topic.tags.map { it.name },
        dot = topic.dot(),
        pinned = topic.pinned,
        unread = topic.unreadCount,
        inactive = topic.inactive,
        onTag = onTag?.let { open -> { open(topic.tags[it]) } },
        expansion =
            topic.preview?.takeIf { expanded }?.let { preview ->
                TopicExpansion(
                    excerpt = preview.firstExcerpt.orEmpty(),
                    latest =
                        preview.latest.map {
                            LatestMessage(
                                it.authorName,
                                it.excerpt.orEmpty(),
                            )
                        },
                    writers = preview.writers.map { it.writer(dark, scheme.tertiary) },
                    images = preview.images,
                    imagesCount = maxOf(preview.imagesCount, preview.images.size),
                    writersCount = maxOf(preview.writersCount, preview.writers.size),
                )
            },
        lead = {
            if (category != null) {
                CategoryMark(category, size = CategoryMarkSize.Sidebar)
            } else if (author != null) {
                val url = pictureUrl(author.avatarUrl, author.avatarDarkUrl, dark)
                Avatar(
                    author.displayName,
                    parseHexColor(author.colour) ?: scheme.tertiary,
                    size = 30.dp,
                    image =
                        if (picture != null && url != null) {
                            { picture(url, Modifier.fillMaxSize()) }
                        } else {
                            null
                        },
                )
            }
        },
        picture = picture,
    )
}

private fun Person.writer(
    dark: Boolean,
    fallback: Color,
): Writer =
    Writer(
        name = displayName,
        color = parseHexColor(colour) ?: fallback,
        picture = pictureUrl(avatarUrl, avatarDarkUrl, dark),
    )

/** "5 min. ago", "Yesterday", "Nov 3", in the device's language (as the web's time_ago). */
fun relativeTime(
    time: Instant?,
    now: Long = System.currentTimeMillis(),
): String =
    time
        ?.let {
            DateUtils
                .getRelativeTimeSpanString(
                    it.toEpochMilli(),
                    now,
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString()
        }.orEmpty()
