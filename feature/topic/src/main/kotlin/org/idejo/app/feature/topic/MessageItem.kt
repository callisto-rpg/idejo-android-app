// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.idejo.app.core.designsystem.component.Avatar
import org.idejo.app.core.designsystem.component.MessageHeader
import org.idejo.app.core.designsystem.component.relativeTime
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.designsystem.theme.parseHexColor
import org.idejo.app.core.designsystem.theme.pictureUrl
import org.idejo.app.core.markdown.IdejoMarkdown
import org.idejo.app.core.markdown.MarkdownActions
import org.idejo.app.core.markdown.MarkdownBody
import org.idejo.app.core.markdown.MdInline
import org.idejo.app.core.markdown.MentionPulse
import org.idejo.app.core.model.Emoji
import org.idejo.app.core.model.Me
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.MessageState
import org.idejo.app.core.model.Reaction

/** What a message needs from its page: the forum's address and emoji, and where taps go. */
@Immutable
data class MessageContext(
    val origin: String,
    val emoji: List<Emoji> = emptyList(),
    /** A name tapped: the user card (BR-09). */
    val onUser: (String) -> Unit = {},
    /** A link in a body, as written. */
    val onLink: (String) -> Unit = {},
    /** A quote or forward tapped: the message it shows (TV-02). */
    val onMessage: (String) -> Unit = {},
    /** A banner's button: another topic (TP-09, TP-15). */
    val onTopic: (String) -> Unit = {},
    /** The member's answers to a poll: message, poll, options (TV-27). */
    val onVote: (String, String, List<String>) -> Unit = { _, _, _ -> },
    /** Polls may take votes here (signed in, the topic open). */
    val mayVote: Boolean = false,
    /** Polls whose vote is being sent. */
    val voting: Set<String> = emptySet(),
    /** The member, whose tags pulse in a glowing message (TV-07). */
    val me: Me? = null,
)

/**
 * One message (TV-02): the author's line, the body drawn from its Markdown, its images and
 * reactions; deleted ones keep their place (TV-08), ignored authors' fold (TV-08), long
 * bodies fold under **Show all** (TV-09), a spoiler covers the whole body (TV-23).
 */
@Composable
fun MessageItem(
    message: Message,
    context: MessageContext,
    modifier: Modifier = Modifier,
    pulse: MentionPulse? = null,
) {
    val open = stringResource(R.string.message_open)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = false) {
                if (message.state == MessageState.Open) contentDescription = open
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Author(message, context)
        when {
            message.state == MessageState.Deleted -> Deleted(message)
            message.author.ignoredByMe -> Ignored(message, context)
            else -> Shown(message, context, pulse)
        }
        Banners(message, context)
    }
}

@Composable
private fun Author(
    message: Message,
    context: MessageContext,
) {
    val author = message.author
    val picture = pictureUrl(author.avatarUrl, author.avatarDarkUrl, IdejoTheme.dark)
    val tap = author.id?.let { id -> Modifier.clickable { context.onUser(id) } } ?: Modifier
    val android = LocalContext.current
    val copy = stringResource(R.string.message_copy_link)
    val permalink =
        message.url?.let { url ->
            Modifier.combinedClickable(
                onLongClickLabel = copy,
                onLongClick = { android.copyLink(url) },
                onClick = {},
            )
        } ?: Modifier
    MessageHeader(
        displayName = author.displayName,
        time = relativeTime(message.openedAt),
        nameModifier = tap,
        timeModifier = permalink,
        userTag = author.userTag,
        role = author.role?.name,
        roleColor = parseHexColor(author.role?.colour ?: author.colour),
        avatar = {
            Avatar(
                author.displayName,
                parseHexColor(author.colour) ?: MaterialTheme.colorScheme.tertiary,
                image =
                    picture?.let { url ->
                        {
                            AsyncImage(
                                absolute(context.origin, url),
                                null,
                                contentScale = ContentScale.Crop,
                            )
                        }
                    },
            )
        },
    )
}

@Composable
private fun Deleted(message: Message) {
    Text(
        stringResource(
            if (message.deletedBy == "moderator") {
                R.string.message_deleted_moderator
            } else {
                R.string.message_deleted_user
            },
        ),
        style = IdejoTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** An ignored author's message, folded until asked (TV-08). */
@Composable
private fun Ignored(
    message: Message,
    context: MessageContext,
) {
    var shown by rememberSaveable(message.id) { mutableStateOf(false) }
    if (shown) {
        Shown(message, context, pulse = null)
    } else {
        TextButton(onClick = { shown = true }) {
            Text(stringResource(R.string.message_ignored, message.author.displayName))
        }
    }
}

@Composable
private fun Shown(
    message: Message,
    context: MessageContext,
    pulse: MentionPulse?,
) {
    var revealed by rememberSaveable(message.id) { mutableStateOf(message.spoiler == null) }
    Box {
        Column(
            Modifier.then(if (revealed) Modifier else Modifier.blur(8.dp).clearAndSetSemantics {}),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            message.repliesTo.filterNot { it.inline }.forEach { ReplyQuote(it, context) }
            message.forwardedFrom?.let { ForwardBox(it, context) }
            // A poll posted on its own: its question is the message (the web's poll_only).
            if (message.poll?.question?.trim() !=
                message.bodyMd?.trim()
            ) {
                Body(message, context, pulse)
            }
            message.poll?.let { PollView(message.id, it, context) }
            MetaLine(message)
            if (message.reactions.isNotEmpty()) Reactions(message.reactions, context)
        }
        if (!revealed) {
            SpoilerCover(
                message.spoiler?.overlay,
                onReveal = { revealed = true },
                Modifier.align(Alignment.Center),
            )
        }
    }
}

/** The whole-message cover: its words, and **Reveal spoiler** (TV-23). */
@Composable
private fun SpoilerCover(
    overlay: String?,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    overlay?.takeIf { it.isNotBlank() }?.let {
        Text(it, style = IdejoTheme.typography.titleSmall)
    }
    TextButton(onClick = onReveal) { Text(stringResource(R.string.message_reveal_spoiler)) }
}

/** The body, folded under **Show all** when taller than the screen (TV-09). */
@Composable
private fun Body(
    message: Message,
    context: MessageContext,
    pulse: MentionPulse?,
) {
    val markdown = message.bodyMd ?: return
    val document =
        remember(markdown, message.mentions) { IdejoMarkdown.parse(markdown, message.mentions) }
    var spoilers by rememberSaveable(message.id) { mutableStateOf(emptySet<Int>()) }
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    var tall by remember { mutableStateOf(false) }
    val limit = LocalWindowInfo.current.containerSize.height
    val screen = with(LocalDensity.current) { limit.toDp() }
    Column {
        Box(
            Modifier
                .animateContentSize()
                .then(if (expanded) Modifier else Modifier.heightIn(max = screen).clipToBounds()),
        ) {
            MarkdownBody(
                document,
                Modifier.onSizeChanged { tall = it.height >= limit },
                actions =
                    MarkdownActions(
                        onLink = context.onLink,
                        onReveal = { spoilers = spoilers + it },
                        emoji = { name -> context.emoji.firstOrNull { it.name == name } },
                    ),
                revealed = spoilers,
                pulse = pulse,
                image = { MessageImage(it, message, context) },
                emojiImage = { emoji ->
                    emoji.imageUrl?.let { url ->
                        AsyncImage(
                            absolute(context.origin, url),
                            emoji.name,
                            Modifier.heightIn(max = 22.dp),
                        )
                    }
                },
            )
        }
        if (tall && !expanded) {
            TextButton(onClick = { expanded = true }) {
                Text(stringResource(R.string.message_show_all))
            }
        }
    }
}

/** An image of the body, sized from `images` when the server knows its size (TV-25). */
@Composable
private fun MessageImage(
    image: MdInline.Image,
    message: Message,
    context: MessageContext,
) {
    val known = message.images.firstOrNull { it.url == image.url }
    val ratio =
        known?.let { k ->
            val w = k.width?.takeIf { it > 0 }
            val h = k.height?.takeIf { it > 0 }
            if (w != null && h != null) w.toFloat() / h else null
        }
    AsyncImage(
        absolute(context.origin, image.url),
        image.alt.ifBlank { known?.alt },
        Modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .then(if (ratio != null) Modifier.aspectRatio(ratio) else Modifier),
        contentScale = ContentScale.Fit,
    )
}
