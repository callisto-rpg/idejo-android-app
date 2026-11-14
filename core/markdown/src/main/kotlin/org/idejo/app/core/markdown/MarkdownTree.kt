// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.markdown

import androidx.compose.runtime.Immutable
import org.idejo.app.core.model.Mention

/**
 * A message's Markdown as the app draws it (TV-20): blocks of inline runs, made by
 * [IdejoMarkdown.parse]. Plain data, so it can be made off the main thread and kept.
 */
@Immutable
data class MarkdownDocument(
    val blocks: List<MdBlock>,
) {
    /** Every `:name:` in the text, to look up before drawing (unknown ones stay as text). */
    val emojiNames: Set<String> by lazy { blocks.flatMap { it.inlines() }.emojiNames() }

    /** How many `||spoiler||` bars it holds, numbered from 0 in reading order (TV-23). */
    val spoilerCount: Int by lazy { blocks.flatMap { it.inlines() }.spoilers() }
}

sealed interface MdBlock {
    data class Paragraph(
        val inlines: List<MdInline>,
    ) : MdBlock

    data class Heading(
        val level: Int,
        val inlines: List<MdInline>,
    ) : MdBlock

    /** `-# small` at the start of a line: small, faint text. */
    data class Small(
        val inlines: List<MdInline>,
    ) : MdBlock

    data class Quote(
        val blocks: List<MdBlock>,
    ) : MdBlock

    data class ListBlock(
        val ordered: Boolean,
        val start: Int,
        val items: List<ListItem>,
    ) : MdBlock

    /** A list item; [checked] is null for one that is not a task. */
    data class ListItem(
        val checked: Boolean?,
        val blocks: List<MdBlock>,
    )

    /** A code block: monospace, scrolled sideways, never highlighted (TV-24). */
    data class Code(
        val text: String,
    ) : MdBlock

    data object Rule : MdBlock

    data class Table(
        val header: List<Cell>,
        val rows: List<List<Cell>>,
    ) : MdBlock

    data class Cell(
        val inlines: List<MdInline>,
        val alignment: Alignment,
    )

    enum class Alignment { Start, Center, End }
}

sealed interface MdInline {
    data class Text(
        val text: String,
    ) : MdInline

    data class Emphasis(
        val children: List<MdInline>,
    ) : MdInline

    data class Strong(
        val children: List<MdInline>,
    ) : MdInline

    data class Strike(
        val children: List<MdInline>,
    ) : MdInline

    /** `==highlight==`. */
    data class Highlight(
        val children: List<MdInline>,
    ) : MdInline

    /** `x^2^`. */
    data class Superscript(
        val children: List<MdInline>,
    ) : MdInline

    /** `||spoiler||`, the [index]th of the message (TV-23). */
    data class Spoiler(
        val index: Int,
        val children: List<MdInline>,
    ) : MdInline

    data class Code(
        val text: String,
    ) : MdInline

    /** A link written in the Markdown, or a bare address (autolink). */
    data class Link(
        val url: String,
        val children: List<MdInline>,
    ) : MdInline

    /** An inline link idejo resolved (TV-21), placed where its text stands. */
    data class InlineLink(
        val mention: Mention,
        val text: String,
    ) : MdInline

    /** An image; what it shows comes from the message's `images` (TV-21, TV-25). */
    data class Image(
        val url: String,
        val alt: String,
    ) : MdInline

    /** `:name:`, drawn when the forum knows the name, else as the text it was. */
    data class Emoji(
        val name: String,
    ) : MdInline

    /** A line break: [hard] for a backslash or two spaces, else a soft one (a space). */
    data class Break(
        val hard: Boolean,
    ) : MdInline
}

internal fun MdBlock.inlines(): List<MdInline> =
    when (this) {
        is MdBlock.Paragraph -> inlines
        is MdBlock.Heading -> inlines
        is MdBlock.Small -> inlines
        is MdBlock.Quote -> blocks.flatMap { it.inlines() }
        is MdBlock.ListBlock -> items.flatMap { item -> item.blocks.flatMap { it.inlines() } }
        is MdBlock.Table -> (header + rows.flatten()).flatMap { it.inlines }
        is MdBlock.Code, MdBlock.Rule -> emptyList()
    }

internal fun MdInline.children(): List<MdInline> =
    when (this) {
        is MdInline.Emphasis -> children
        is MdInline.Strong -> children
        is MdInline.Strike -> children
        is MdInline.Highlight -> children
        is MdInline.Superscript -> children
        is MdInline.Spoiler -> children
        is MdInline.Link -> children
        else -> emptyList()
    }

private fun List<MdInline>.emojiNames(): Set<String> =
    flatMap { inline ->
        if (inline is MdInline.Emoji) listOf(inline.name) else inline.children().emojiNames()
    }.toSet()

private fun List<MdInline>.spoilers(): Int =
    sumOf { inline -> (if (inline is MdInline.Spoiler) 1 else 0) + inline.children().spoilers() }
