// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.idejo.app.core.data.ContinuedFrom
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.markdown.IdejoMarkdown
import org.idejo.app.core.markdown.plainText

/**
 * "Continued from {name} in “{topic}”" over a topic started from a message (idejo TP-09),
 * as the web's quote, with the message's first words; tapping it opens that message.
 */
@Composable
internal fun ContinuedFromQuote(
    from: ContinuedFrom,
    onMessage: (String) -> Unit,
) = QuoteBox(Modifier.clickable { onMessage(from.message.id) }) {
    Text(
        stringResource(
            R.string.topic_continued_from,
            from.message.author.displayName,
            from.topicTitle,
        ),
        style = IdejoTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
    )
    val markdown = from.message.bodyMd.orEmpty()
    val spoiler = stringResource(org.idejo.app.core.markdown.R.string.markdown_spoiler)
    val words =
        remember(markdown, spoiler) { IdejoMarkdown.parse(markdown).plainText(spoiler).trim() }
    if (words.isNotEmpty()) {
        Text(
            words,
            style = IdejoTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
