// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.markdown.IdejoMarkdown
import org.idejo.app.core.markdown.MarkdownActions
import org.idejo.app.core.markdown.MarkdownBody
import org.idejo.app.core.model.Forwarded
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.ReplyTo

/**
 * The message this one replies to, as the web's box: "In reply to NAME" and its excerpt;
 * a tap leads to it (TV-02). Quotes written inside the body (`inline`) stay in the body.
 */
@Composable
internal fun ReplyQuote(
    reply: ReplyTo,
    context: MessageContext,
) = QuoteBox(Modifier.clickable { context.onMessage(reply.messageId) }) {
    Text(
        stringResource(R.string.message_in_reply_to, reply.authorName),
        style = IdejoTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
    )
    reply.excerpt?.takeIf { it.isNotBlank() }?.let {
        Text(
            it,
            style = IdejoTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A forwarded message, as the web's box: who wrote it and where, then its text, cut past
 * eight lines with **Show full message**; only "A message forwarded from a topic you cannot
 * view" when the member may not read it.
 */
@Composable
internal fun ForwardBox(
    forwarded: Forwarded,
    context: MessageContext,
) = QuoteBox {
    val author = forwarded.authorName
    val place = forwarded.topicTitle
    when {
        !forwarded.visible || author == null -> {
            Text(
                stringResource(R.string.message_forwarded_hidden),
                style = IdejoTheme.typography.labelMedium,
            )
        }

        place == null -> {
            Text(
                stringResource(R.string.message_forwarded_from_hidden, author),
                style = IdejoTheme.typography.labelMedium,
            )
        }

        else -> {
            Column {
                Text(
                    stringResource(R.string.message_forwarded_from, author),
                    style = IdejoTheme.typography.labelMedium,
                )
                Text(
                    place,
                    Modifier.clickable { context.onMessage(forwarded.messageId) },
                    style = IdejoTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    val body = forwarded.bodyMd?.takeIf { forwarded.visible && it.isNotBlank() }
    if (body != null) ForwardedBody(body, context)
}

@Composable
private fun ForwardedBody(
    body: String,
    context: MessageContext,
) {
    val document = remember(body) { IdejoMarkdown.parse(body, emptyList()) }
    var full by rememberSaveable(body) { mutableStateOf(false) }
    var long by remember { mutableStateOf(false) }
    val lines = IdejoTheme.typography.bodyMedium.lineHeight
    val limit = with(LocalDensity.current) { (lines * FORWARD_LINES).toDp() }
    val limitPx = with(LocalDensity.current) { limit.roundToPx() }
    Column {
        Box(if (full) Modifier else Modifier.heightIn(max = limit).clipToBounds()) {
            MarkdownBody(
                document,
                Modifier.onSizeChanged { long = it.height > limitPx },
                actions = MarkdownActions(onLink = context.onLink),
            )
        }
        if (long && !full) {
            TextButton(onClick = { full = true }) {
                Text(stringResource(R.string.message_show_full))
            }
        }
    }
}

/** The web's quote box: a line at its start, the text beside it. */
@Composable
private fun QuoteBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
    Box(
        Modifier
            .width(3.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
    )
    Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        content()
    }
}

/**
 * The thin banners under a message (TP-09, TP-15): each topic started from it, and each
 * topic messages after it were moved to, with a button to open it.
 */
@Composable
internal fun Banners(
    message: Message,
    context: MessageContext,
) {
    message.continuedIn.forEach { topic ->
        Banner(
            stringResource(R.string.message_continued_in),
            topic.title,
            stringResource(R.string.message_continue_topic),
            stringResource(R.string.message_continue_topic_named, topic.title),
        ) { context.onTopic(topic.topicId) }
    }
    message.movedAfter.forEach { moved ->
        Banner(
            pluralStringResource(
                if (moved.newTopic) R.plurals.message_moved_new else R.plurals.message_moved_other,
                moved.count,
                moved.count,
            ),
            moved.title,
            stringResource(R.string.message_open_topic),
            stringResource(R.string.message_open_topic_named, moved.title),
        ) { context.onTopic(moved.topicId) }
    }
}

@Composable
private fun Banner(
    text: String,
    title: String,
    action: String,
    label: String,
    onOpen: () -> Unit,
) = Surface(
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = BANNER_ALPHA),
    modifier = Modifier.fillMaxWidth(),
) {
    Row(
        Modifier.padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val soft = MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = soft)) { append(text) }
                append(" ")
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(title) }
            },
            Modifier.weight(1f),
            style = IdejoTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Thin, as the web's: a small button whose touch target stays 48 dp (AX).
        FilledTonalButton(
            onClick = onOpen,
            Modifier
                .heightIn(min = 30.dp)
                .semantics { contentDescription = label },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        ) { Text(action, style = IdejoTheme.typography.labelMedium) }
    }
}

/** "scheduled message" beside "message edited by a moderator", as the web's. */
@Composable
internal fun MetaLine(message: Message) {
    val parts =
        buildList {
            if (message.scheduled) add(stringResource(R.string.message_scheduled))
            if (message.moderatorEdited) add(stringResource(R.string.message_moderator_edited))
        }
    if (parts.isEmpty()) return
    Text(
        parts.joinToString("   "),
        style = IdejoTheme.typography.labelSmall,
        fontStyle = FontStyle.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A message's permalink on the clipboard (TV-02: long-press on its time). */
internal fun Context.copyLink(url: String) {
    getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(getString(R.string.message_copy_link), url))
    // Android 13 and later say so themselves.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, R.string.message_link_copied, Toast.LENGTH_SHORT).show()
    }
}

private const val FORWARD_LINES = 8
private const val BANNER_ALPHA = 0.45f
