// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import org.idejo.app.core.markdown.IdejoMarkdown
import org.idejo.app.core.markdown.excerpt
import org.idejo.app.core.model.Message

/**
 * [message] as a reply quotes it (CO-16): its author and first words, a whole-message
 * spoiler read as [spoiler] (or its cover's words through [spoilerLabel]).
 */
fun quoteOf(
    message: Message,
    spoiler: String,
    spoilerLabel: (String) -> String,
): Quoted {
    val cover = message.spoiler
    val excerpt =
        if (cover != null) {
            cover.overlay?.let(spoilerLabel) ?: spoiler
        } else {
            IdejoMarkdown.parse(message.bodyMd.orEmpty(), message.mentions).excerpt(spoiler)
        }
    return Quoted(message.id, message.author.displayName, excerpt)
}

/**
 * The web's inline reply (idejo `InlineReplies.markdown/1`): a block quote of two links to
 * the message's permalink, [head] ("Replying to NAME:") and the excerpt, escaped so the words
 * quoted never mention anyone.
 */
fun inlineQuote(
    quote: Quoted,
    head: String,
): String {
    val path = "/m/${quote.id}"
    val first = "> [${escape(head)}]($path)"
    return if (quote.excerpt.isEmpty()) first else "$first\n> [${escape(quote.excerpt)}]($path)"
}

private fun escape(text: String): String = SPECIAL.replace(text) { "\\" + it.value }

private val SPECIAL = Regex("""[\\`*_\[\]<>~=|^!&]""")
