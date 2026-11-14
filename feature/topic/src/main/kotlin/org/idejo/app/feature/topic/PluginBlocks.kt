// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.idejo.app.core.markdown.IdejoMarkdown
import org.idejo.app.core.markdown.MarkdownActions
import org.idejo.app.core.markdown.MarkdownBody
import org.idejo.app.core.model.PluginBlock

/**
 * A message's plugin blocks (TV-26), in the order they were added: `button` and `card`
 * native, `forum` as another forum's card, `video` and `iframe` as a card that loads the
 * page only when tapped ([FrameBlock]). Blocks the app does not know are skipped.
 */
@Composable
internal fun PluginBlocks(
    blocks: List<PluginBlock>,
    context: MessageContext,
) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    blocks.forEach { block ->
        when (block.block) {
            "button" -> LinkButton(block, context)
            "card" -> CardButton(block, context)
            "forum" -> ForumBlock(block, context)
            "video", "iframe" -> FrameBlock(block, context)
        }
    }
}

/** `button`: its label; its address opens outside the app, as any link. */
@Composable
private fun LinkButton(
    block: PluginBlock,
    context: MessageContext,
) {
    val label = block.text("label") ?: return
    val url = block.text("url") ?: return
    Button(onClick = { context.onLink(url) }) { Text(label) }
}

/** `card`: a button opening the card's text over the page, as the web's. */
@Composable
private fun CardButton(
    block: PluginBlock,
    context: MessageContext,
) {
    val label = block.text("label") ?: return
    var open by rememberSaveable { mutableStateOf(false) }
    Button(onClick = { open = true }) { Text(label) }
    if (open) PluginCard(label, block.text("body_md").orEmpty(), context) { open = false }
}

@Composable
private fun PluginCard(
    label: String,
    markdown: String,
    context: MessageContext,
    onClose: () -> Unit,
) = Dialog(onDismissRequest = onClose) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(label, style = MaterialTheme.typography.titleLarge)
            val document = remember(markdown) { IdejoMarkdown.parse(markdown) }
            var revealed by remember { mutableStateOf(emptySet<Int>()) }
            MarkdownBody(
                document,
                actions =
                    MarkdownActions(
                        onLink = context.onLink,
                        onReveal = { revealed = revealed + it },
                    ),
                revealed = revealed,
            )
            TextButton(onClick = onClose, Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.card_close))
            }
        }
    }
}

/** `forum`: another forum, as idejo ID mode shows it (Join and Open wait for M4). */
@Composable
private fun ForumBlock(
    block: PluginBlock,
    context: MessageContext,
) {
    val name = block.text("name") ?: return
    val url = block.text("url") ?: return
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        ForumCard(name, hostOf(url), block.text("logo"), context.origin, Modifier.padding(12.dp))
    }
}

/** An address's host, as the web's captions name it. */
internal fun hostOf(url: String): String =
    url
        .substringAfter("://")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore(':')
