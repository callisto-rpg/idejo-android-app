// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.markdown

/** How long an excerpt is, as idejo's `Forum.excerpt/2`. */
const val EXCERPT_LENGTH = 140

/**
 * A message's words as plain text, cut like idejo's excerpts (CO-16's inline quotes): blocks
 * and line breaks part words, emoji read as their names, images as their descriptions,
 * `||spoilers||` as [spoiler]; past [length], cut on a word with "…".
 */
fun MarkdownDocument.excerpt(
    spoiler: String,
    length: Int = EXCERPT_LENGTH,
): String {
    val words =
        blocks
            .joinToString(" ") { it.plain(spoiler) }
            .replace(WHITESPACE, " ")
            .trim()
    if (words.length <= length) return words
    val cut = words.take(length)
    val space = cut.lastIndexOf(' ')
    return (if (space > 0) cut.take(space) else cut).trimEnd() + "…"
}

private fun MdBlock.plain(spoiler: String): String =
    when (this) {
        is MdBlock.Code -> {
            text
        }

        is MdBlock.Quote -> {
            blocks.joinToString(" ") { it.plain(spoiler) }
        }

        is MdBlock.ListBlock -> {
            items.joinToString(" ") { i ->
                i.blocks.joinToString(" ") {
                    it.plain(spoiler)
                }
            }
        }

        else -> {
            inlines().joinToString("") { it.plain(spoiler) }
        }
    }

private fun MdInline.plain(spoiler: String): String =
    when (this) {
        is MdInline.Text -> text
        is MdInline.Code -> text
        is MdInline.InlineLink -> text
        is MdInline.Image -> alt
        is MdInline.Emoji -> ":$name:"
        is MdInline.Break -> " "
        is MdInline.Spoiler -> spoiler
        else -> children().joinToString("") { it.plain(spoiler) }
    }

private val WHITESPACE = Regex("\\s+")
