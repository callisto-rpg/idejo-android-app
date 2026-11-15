// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.markdown.IdejoMarkdown
import org.idejo.app.core.markdown.excerpt
import org.idejo.app.core.model.Message

/** **Replying to NAME:** over the box, **Cancel reply** until the first Enter (CO-16). */
@Composable
internal fun ReplyingBox(
    quote: Quoted,
    onCancel: () -> Unit,
) = QuoteCard(
    stringResource(R.string.composer_replying, quote.authorName),
    quote.excerpt,
    stringResource(R.string.composer_cancel_reply).takeUnless { quote.sent },
    onCancel,
)

/** The message forwarded, over the box, **Cancel forward** until the first Enter (CO-17). */
@Composable
internal fun ForwardBox(
    forwarding: Forwarding,
    cancellable: Boolean,
    onCancel: () -> Unit,
) {
    val message = forwarding.message
    val name = message.author.displayName
    val head =
        forwarding.topicTitle?.let { stringResource(R.string.composer_forwarded, name, it) }
            ?: stringResource(R.string.composer_forwarded_here, name)
    val spoiler = stringResource(R.string.composer_spoiler)
    val body =
        remember(message) {
            IdejoMarkdown
                .parse(message.bodyMd.orEmpty(), message.mentions)
                .excerpt(spoiler, FORWARD_LENGTH)
        }
    QuoteCard(
        head,
        body,
        stringResource(R.string.composer_cancel_forward).takeIf {
            cancellable
        },
        onCancel,
    )
}

@Composable
private fun QuoteCard(
    head: String,
    text: String,
    cancel: String?,
    onCancel: () -> Unit,
) = Surface(
    Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(head, Modifier.weight(1f), style = IdejoTheme.typography.labelLarge)
            if (cancel != null) TextButton(onClick = onCancel) { Text(cancel) }
        }
        Text(
            text,
            style = IdejoTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = QUOTE_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** What **Reply** does on a message, [above] or below the open one (CO-16). */
@Composable
fun rememberReply(composer: Composer): (Message, Boolean) -> Unit {
    val spoiler = stringResource(R.string.composer_spoiler)
    val label = stringResource(R.string.composer_spoiler_label)
    val head = stringResource(R.string.composer_replying)
    return remember(composer, spoiler, label, head) {
        { message, above ->
            val quote = quoteOf(message, spoiler) { label.format(it) }
            composer.reply(quote, inlineQuote(quote, head.format(quote.authorName)), above)
        }
    }
}

/** Enter on an emptied edit (CO-18): "Are you sure you want to delete your own message?" */
@Composable
internal fun DeleteAsk(events: ComposerEvents) =
    AlertDialog(
        onDismissRequest = events.onCancelDelete,
        text = { Text(stringResource(R.string.composer_delete_ask)) },
        confirmButton = {
            TextButton(
                onClick = events.onConfirmDelete,
            ) { Text(stringResource(R.string.composer_yes)) }
        },
        dismissButton = {
            TextButton(
                onClick = events.onCancelDelete,
            ) { Text(stringResource(R.string.composer_no)) }
        },
    )

private const val QUOTE_LINES = 8
private const val FORWARD_LENGTH = 600
