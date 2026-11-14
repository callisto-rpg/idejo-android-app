// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.markdown

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import org.idejo.app.core.model.Mention

/**
 * idejo's Markdown dialect (TV-20): GitHub's (tables, strikethrough, task lists, bare
 * links) plus `==highlight==`, `x^2^`, `-# small`, `||spoiler||` and `:emoji:`. Raw HTML is
 * shown as the text it was, never rendered.
 *
 * Inline links (`@user`, `+role`, `#topic`, `/category`, `$tag`, `&forum`) are never
 * worked out from their signs (TV-21): the message's [Mention]s say what they are, and
 * each is drawn with the words readers see wherever its `source` stands in the Markdown,
 * ignoring case, outside code and links (its `text` on servers before `source`).
 */
object IdejoMarkdown {
    private val parser: Parser =
        Parser
            .builder()
            .extensions(
                listOf(
                    TablesExtension.create(),
                    StrikethroughExtension.create(),
                    TaskListItemsExtension.create(),
                    AutolinkExtension.create(),
                ),
            ).customDelimiterProcessor(WrapProcessor('=', 2, ::HighlightNode))
            .customDelimiterProcessor(WrapProcessor('^', 1, ::SuperscriptNode))
            .customDelimiterProcessor(WrapProcessor('|', 2, ::SpoilerNode))
            .customBlockParserFactory(SmallBlockParser.Factory())
            .build()

    fun parse(
        markdown: String,
        mentions: List<Mention> = emptyList(),
    ): MarkdownDocument {
        val converter = Converter(mentions)
        return MarkdownDocument(converter.blocks(parser.parse(markdown)))
    }
}

private class Converter(
    mentions: List<Mention>,
) {
    /** Each mention by how it is written; longest first, so `@robin_b` is not `@robin`. */
    private val mentions: List<Pair<String, Mention>> =
        mentions
            .mapNotNull { mention ->
                (mention.source ?: mention.text).takeIf { it.isNotEmpty() }?.let { it to mention }
            }.sortedByDescending { it.first.length }
    private var spoilers = 0

    fun blocks(parent: Node): List<MdBlock> = parent.children().mapNotNull(::block)

    @Suppress("CyclomaticComplexMethod") // One branch per kind of block.
    private fun block(node: Node): MdBlock? =
        when (node) {
            is Paragraph -> {
                MdBlock.Paragraph(inlines(node))
            }

            is Heading -> {
                MdBlock.Heading(node.level, inlines(node))
            }

            is SmallBlock -> {
                MdBlock.Small(inlines(node))
            }

            is BlockQuote -> {
                MdBlock.Quote(blocks(node))
            }

            is BulletList -> {
                MdBlock.ListBlock(false, 1, items(node))
            }

            is OrderedList -> {
                MdBlock.ListBlock(true, node.markerStartNumber ?: 1, items(node))
            }

            is FencedCodeBlock -> {
                MdBlock.Code(node.literal.removeSuffix("\n"))
            }

            is IndentedCodeBlock -> {
                MdBlock.Code(node.literal.removeSuffix("\n"))
            }

            is ThematicBreak -> {
                MdBlock.Rule
            }

            is TableBlock -> {
                table(node)
            }

            // Raw HTML is never rendered: it shows as typed.
            is HtmlBlock -> {
                MdBlock.Paragraph(listOf(MdInline.Text(node.literal.trimEnd())))
            }

            else -> {
                null
            }
        }

    private fun items(list: Node): List<MdBlock.ListItem> =
        list.children().filterIsInstance<ListItem>().map { item ->
            val marker =
                item.children().firstOrNull { it is TaskListItemMarker }
                    ?: item.firstChild
                        ?.firstChild
                        ?.takeIf { it is TaskListItemMarker }
            MdBlock.ListItem((marker as? TaskListItemMarker)?.isChecked, blocks(item))
        }

    private fun table(table: TableBlock): MdBlock.Table {
        val sections = table.children()
        val head = sections.filterIsInstance<TableHead>().flatMap { it.children() }
        val body = sections.filterIsInstance<TableBody>().flatMap { it.children() }
        return MdBlock.Table(
            header =
                head
                    .filterIsInstance<TableRow>()
                    .firstOrNull()
                    ?.let(::cells)
                    .orEmpty(),
            rows = body.filterIsInstance<TableRow>().map(::cells),
        )
    }

    private fun cells(row: TableRow): List<MdBlock.Cell> =
        row.children().filterIsInstance<TableCell>().map { cell ->
            val alignment =
                when (cell.alignment) {
                    TableCell.Alignment.CENTER -> MdBlock.Alignment.Center
                    TableCell.Alignment.RIGHT -> MdBlock.Alignment.End
                    else -> MdBlock.Alignment.Start
                }
            MdBlock.Cell(inlines(cell), alignment)
        }

    private fun inlines(parent: Node): List<MdInline> =
        parent
            .children()
            .flatMap(::inline)
            .joinTexts()
            .flatMap(::placeLinks)
            .flatMap(::placeEmoji)

    /** Inlines inside a link: no inline links of idejo's there, emoji still. */
    private fun plainInlines(parent: Node): List<MdInline> =
        parent
            .children()
            .flatMap(::inline)
            .joinTexts()
            .flatMap(::placeEmoji)

    @Suppress("CyclomaticComplexMethod") // One branch per kind of inline.
    private fun inline(node: Node): List<MdInline> =
        when (node) {
            is Text -> {
                listOf(MdInline.Text(node.literal))
            }

            is Emphasis -> {
                listOf(MdInline.Emphasis(inlines(node)))
            }

            is StrongEmphasis -> {
                listOf(MdInline.Strong(inlines(node)))
            }

            is Strikethrough -> {
                listOf(MdInline.Strike(inlines(node)))
            }

            is HighlightNode -> {
                listOf(MdInline.Highlight(inlines(node)))
            }

            is SuperscriptNode -> {
                listOf(MdInline.Superscript(inlines(node)))
            }

            is SpoilerNode -> {
                val index = spoilers++
                listOf(MdInline.Spoiler(index, inlines(node)))
            }

            is Code -> {
                listOf(MdInline.Code(node.literal))
            }

            is Link -> {
                listOf(MdInline.Link(node.destination, plainInlines(node)))
            }

            is Image -> {
                listOf(MdInline.Image(node.destination, plainText(node)))
            }

            is HardLineBreak -> {
                listOf(MdInline.Break(hard = true))
            }

            is SoftLineBreak -> {
                listOf(MdInline.Break(hard = false))
            }

            is HtmlInline -> {
                listOf(MdInline.Text(node.literal))
            }

            is TaskListItemMarker -> {
                emptyList()
            }

            else -> {
                node.children().flatMap(::inline)
            }
        }

    /** Each [Mention] where it is written, as a word of its own. */
    private fun placeLinks(inline: MdInline): List<MdInline> {
        if (inline !is MdInline.Text || mentions.isEmpty()) return listOf(inline)
        val text = inline.text
        val out = mutableListOf<MdInline>()
        var from = 0
        var at = 0
        while (at < text.length) {
            val found = mentions.firstOrNull { (written, _) -> text.standsAt(written, at) }
            if (found == null) {
                at++
                continue
            }
            val (written, mention) = found
            if (at > from) out += MdInline.Text(text.substring(from, at))
            out += MdInline.InlineLink(mention, mention.text)
            at += written.length
            from = at
        }
        if (from < text.length) out += MdInline.Text(text.substring(from))
        return out
    }

    private fun placeEmoji(inline: MdInline): List<MdInline> {
        if (inline !is MdInline.Text || ':' !in inline.text) return listOf(inline)
        val out = mutableListOf<MdInline>()
        var from = 0
        for (match in EMOJI.findAll(inline.text)) {
            if (match.range.first > from) {
                out += MdInline.Text(inline.text.substring(from, match.range.first))
            }
            out += MdInline.Emoji(match.groupValues[1])
            from = match.range.last + 1
        }
        if (from < inline.text.length) out += MdInline.Text(inline.text.substring(from))
        return out
    }

    private companion object {
        val EMOJI = Regex(":([A-Za-z0-9_+-]+):")
    }
}

/** The words of an image's description. */
private fun plainText(node: Node): String =
    node.children().joinToString("") { child ->
        when (child) {
            is Text -> child.literal
            is Code -> child.literal
            is SoftLineBreak, is HardLineBreak -> " "
            else -> plainText(child)
        }
    }

/** [word] stands at [index], in any case, with nothing word-like right before or after. */
private fun String.standsAt(
    word: String,
    index: Int,
): Boolean {
    if (!regionMatches(index, word, 0, word.length, ignoreCase = true)) return false
    val before = getOrNull(index - 1)
    val after = getOrNull(index + word.length)
    return (before == null || !before.isWordPart()) && (after == null || !after.isWordPart())
}

private fun Char.isWordPart(): Boolean = isLetterOrDigit() || this == '_' || this == '-'

private fun Node.children(): List<Node> = generateSequence(firstChild) { it.next }.toList()

/** commonmark splits text at signs it tried as delimiters; joined again here. */
private fun List<MdInline>.joinTexts(): List<MdInline> {
    val out = mutableListOf<MdInline>()
    for (inline in this) {
        val last = out.lastOrNull()
        if (inline is MdInline.Text && last is MdInline.Text) {
            out[out.lastIndex] = MdInline.Text(last.text + inline.text)
        } else {
            out += inline
        }
    }
    return out
}
