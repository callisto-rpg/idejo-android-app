// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.markdown

import org.commonmark.node.Block
import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.node.Node
import org.commonmark.parser.InlineParser
import org.commonmark.parser.SourceLine
import org.commonmark.parser.SourceLines
import org.commonmark.parser.block.AbstractBlockParser
import org.commonmark.parser.block.AbstractBlockParserFactory
import org.commonmark.parser.block.BlockContinue
import org.commonmark.parser.block.BlockStart
import org.commonmark.parser.block.MatchedBlockParser
import org.commonmark.parser.block.ParserState
import org.commonmark.parser.delimiter.DelimiterProcessor
import org.commonmark.parser.delimiter.DelimiterRun

// idejo's additions to GitHub's Markdown (idejo's `Idejo.Markdown`, the API guide §5):
// `==highlight==`, `x^2^`, `||spoiler||` and `-# small`. Written here from the API guide's
// table; nothing of idejo's code is used.

internal class HighlightNode : CustomNode()

internal class SuperscriptNode : CustomNode()

internal class SpoilerNode : CustomNode()

internal class SmallBlock : CustomBlock()

/**
 * Wraps what stands between a pair of [char] runs of exactly [length] in a node from
 * [make], as the strikethrough extension does for `~~`.
 */
internal class WrapProcessor(
    private val char: Char,
    private val length: Int,
    private val make: () -> Node,
) : DelimiterProcessor {
    override fun getOpeningCharacter() = char

    override fun getClosingCharacter() = char

    override fun getMinLength() = length

    override fun process(
        openingRun: DelimiterRun,
        closingRun: DelimiterRun,
    ): Int {
        if (openingRun.length() < length || closingRun.length() < length) return 0
        val opener = openingRun.opener
        val wrapper = make()
        var node = opener.next
        val closer = closingRun.closer
        while (node != null && node !== closer) {
            val next = node.next
            wrapper.appendChild(node)
            node = next
        }
        opener.insertAfter(wrapper)
        return length
    }
}

/** `-# text` at the start of a line (as Discord and idejo): one line of small text. */
internal class SmallBlockParser(
    private val line: SourceLine,
) : AbstractBlockParser() {
    private val block = SmallBlock()

    override fun getBlock(): Block = block

    override fun tryContinue(parserState: ParserState): BlockContinue? = BlockContinue.none()

    override fun parseInlines(inlineParser: InlineParser) {
        inlineParser.parse(SourceLines.of(line), block)
    }

    class Factory : AbstractBlockParserFactory() {
        override fun tryStart(
            state: ParserState,
            matchedBlockParser: MatchedBlockParser,
        ): BlockStart? {
            if (state.indent >= CODE_INDENT) return BlockStart.none()
            val content = state.line.content
            val start = state.nextNonSpaceIndex
            if (!content.startsWith(MARK, start)) return BlockStart.none()
            val after = start + MARK.length
            val rest = state.line.substring(after, content.length)
            val text = rest.content.trimStart()
            val trimmed = rest.substring(rest.content.length - text.length, rest.content.length)
            return BlockStart.of(SmallBlockParser(trimmed)).atIndex(content.length)
        }
    }

    private companion object {
        const val MARK = "-# "
        const val CODE_INDENT = 4
    }
}
