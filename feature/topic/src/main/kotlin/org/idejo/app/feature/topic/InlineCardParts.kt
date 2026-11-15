// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.data.InlineCard
import org.idejo.app.core.designsystem.component.Avatar
import org.idejo.app.core.designsystem.component.CategoryMark
import org.idejo.app.core.designsystem.component.relativeTime
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.designsystem.theme.parseHexColor
import org.idejo.app.core.designsystem.theme.pictureUrl
import org.idejo.app.core.markdown.IdejoMarkdown
import org.idejo.app.core.markdown.plainText
import org.idejo.app.core.model.Topic

/** `#topic`: category, title, starter, count, time, tags, opening words; Open, Follow. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TopicCard(
    card: InlineCard.Topic,
    state: InlineCardUi,
    actions: InlineCardActions,
) = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    val topic = card.topic
    card.category?.let { category ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryMark(category)
            Meta(category.title)
        }
    }
    Title(topic.title)
    Meta(topicMeta(topic))
    if (topic.tags.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            topic.tags.forEach { TagChip(it, actions.onTag) }
        }
    }
    card.opening?.let { Excerpt(it) }
    OpenRow(stringResource(R.string.card_open_topic), { actions.onTopic(topic.id) }) {
        if (state.signedIn) {
            OutlinedButton(
                onClick = { actions.onFollow(!topic.followed) },
                enabled = !state.busy,
            ) {
                Text(
                    stringResource(
                        if (topic.followed) R.string.topic_unfollow else R.string.topic_follow,
                    ),
                )
            }
        }
    }
}

/** "Started by …" (never on a topical topic) · N messages · when. */
@Composable
private fun topicMeta(topic: Topic): String {
    val parts =
        listOfNotNull(
            topic.author?.takeUnless { topic.topical }?.let {
                stringResource(R.string.card_started_by, it.displayName)
            },
            pluralStringResource(R.plurals.card_messages, topic.messagesCount, topic.messagesCount),
            topic.lastActivityAt?.let { relativeTime(it) },
        )
    return parts.joinToString(" · ")
}

/** The opening message's first words, as text (the web's 200 characters). */
@Composable
private fun Excerpt(markdown: String) {
    val spoiler = stringResource(org.idejo.app.core.markdown.R.string.markdown_spoiler)
    val words =
        remember(markdown, spoiler) {
            val plain = IdejoMarkdown.parse(markdown).plainText(spoiler).trim()
            if (plain.length > EXCERPT) plain.take(EXCERPT).trimEnd() + "…" else plain
        }
    if (words.isNotEmpty()) Text(words, style = IdejoTheme.typography.bodyMedium)
}

/** `/category`: mark and name, description, topic count, latest topics; Open category. */
@Composable
internal fun CategoryCard(
    card: InlineCard.Category,
    actions: InlineCardActions,
) = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    val category = card.category
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryMark(category)
        Title(category.title)
    }
    category.description?.takeIf { it.isNotBlank() }?.let {
        Text(it, style = IdejoTheme.typography.bodyMedium)
    }
    Meta(
        pluralStringResource(R.plurals.card_topics, category.topicsCount, category.topicsCount),
    )
    TopicLinks(card.topics, actions.onTopic)
    OpenRow(stringResource(R.string.card_open_category), { actions.onCategory(category.id) })
}

/** `$tag`: where it belongs, its latest topics; Filter by tag. */
@Composable
internal fun TagCard(
    card: InlineCard.Tag,
    actions: InlineCardActions,
) = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Title("$" + card.tag.name)
    Meta(
        if (card.categories.isEmpty()) {
            stringResource(R.string.card_tag_everywhere)
        } else {
            stringResource(
                R.string.card_tag_of,
                card.categories.joinToString(", ") { it.title },
            )
        },
    )
    TopicLinks(card.topics, actions.onTopic)
    if (card.topics.isEmpty()) Meta(stringResource(R.string.card_tag_empty))
    val slug = card.tag.slug ?: card.tag.id
    OpenRow(stringResource(R.string.card_filter_by_tag), { actions.onTag(slug) })
}

/** `+role`: in its colour, its member count and first members (no Open in M1). */
@Composable
internal fun RoleCard(
    card: InlineCard.Role,
    origin: String,
    actions: InlineCardActions,
) = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    val role = card.role
    Text(
        "+" + role.name,
        Modifier.semantics { heading() },
        style = IdejoTheme.typography.titleLarge,
        color = parseHexColor(role.colour) ?: MaterialTheme.colorScheme.onSurface,
    )
    Meta(pluralStringResource(R.plurals.card_members, role.membersCount, role.membersCount))
    role.members.take(SHOWN).forEach { person ->
        val url = pictureUrl(person.avatarUrl, person.avatarDarkUrl, IdejoTheme.dark)
        Row(
            Modifier.clickable { person.id?.let(actions.onUser) }.padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                person.displayName,
                parseHexColor(person.colour) ?: MaterialTheme.colorScheme.tertiary,
                size = 24.dp,
                image =
                    url?.let {
                        {
                            AsyncImage(
                                absolute(origin, it),
                                null,
                                contentScale = ContentScale.Crop,
                            )
                        }
                    },
            )
            Text(person.displayName, style = IdejoTheme.typography.bodyMedium)
        }
    }
}

/** **Latest topics**: up to three titles, each opening its topic. */
@Composable
private fun TopicLinks(
    topics: List<Topic>,
    onTopic: (String) -> Unit,
) {
    if (topics.isEmpty()) return
    Column {
        Meta(stringResource(R.string.card_latest_topics))
        topics.forEach { topic ->
            TextButton(onClick = { onTopic(topic.id) }) { Text(topic.title) }
        }
    }
}

@Composable
private fun Title(text: String) =
    Text(text, Modifier.semantics { heading() }, style = IdejoTheme.typography.titleLarge)

@Composable
private fun Meta(text: String) =
    Text(
        text,
        style = IdejoTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

private const val EXCERPT = 200
private const val SHOWN = 3
