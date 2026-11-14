// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.markdown

/**
 * The document's words without their styles, blocks joined by a space, for a cut excerpt (a
 * user card's bio, BR-09). A `||spoiler||` reads [spoiler], never its words, as the web's
 * previews say *(spoiler)*.
 */
fun MarkdownDocument.plainText(spoiler: String): String =
    blocks
        .joinToString(" ") { it.plain(spoiler) }
        .replace(Regex("\\s+"), " ")
        .trim()

private fun MdBlock.plain(spoiler: String): String =
    when (this) {
        is MdBlock.Paragraph -> {
            inlines.words(spoiler)
        }

        is MdBlock.Heading -> {
            inlines.words(spoiler)
        }

        is MdBlock.Small -> {
            inlines.words(spoiler)
        }

        is MdBlock.Quote -> {
            blocks.joinToString(" ") { it.plain(spoiler) }
        }

        is MdBlock.ListBlock -> {
            items.joinToString(" ") { item ->
                item.blocks.joinToString(" ") {
                    it.plain(spoiler)
                }
            }
        }

        is MdBlock.Code -> {
            text
        }

        is MdBlock.Table -> {
            (listOf(header) + rows).flatten().joinToString(" ") {
                it.inlines.words(spoiler)
            }
        }

        MdBlock.Rule -> {
            ""
        }
    }

private fun List<MdInline>.words(spoiler: String): String =
    joinToString("") { inline ->
        when (inline) {
            is MdInline.Spoiler -> "($spoiler)"
            is MdInline.Text -> inline.text
            is MdInline.Code -> inline.text
            is MdInline.Break -> " "
            is MdInline.InlineLink -> inline.text
            is MdInline.Image -> inline.alt
            is MdInline.Emoji -> ":${inline.name}:"
            else -> inline.children().words(spoiler)
        }
    }
