// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** One format: what to type, what it is, and whether to draw it (not headings, as the web). */
internal class Format(
    val syntax: String,
    val label: String,
    val shown: Boolean = true,
)

@Composable
internal fun formats(): List<Format> {
    @Composable fun s(id: Int) = stringResource(id)
    return listOf(
        Format("**${s(R.string.format_bold_word)}**", s(R.string.format_bold)),
        Format("*${s(R.string.format_italics_word)}*", s(R.string.format_italics)),
        Format("~~${s(R.string.format_strike_word)}~~", s(R.string.format_strike)),
        Format("==${s(R.string.format_highlight_word)}==", s(R.string.format_highlight)),
        Format("x^2^", s(R.string.format_superscript)),
        Format("-# ${s(R.string.format_small_word)}", s(R.string.format_small)),
        Format("||${s(R.string.format_spoiler_word)}||", s(R.string.format_spoiler)),
        Format("`${s(R.string.format_code_word)}`", s(R.string.format_code)),
        Format("```\n${s(R.string.format_code_block_words)}\n```", s(R.string.format_code_block)),
        Format("> ${s(R.string.format_quote_word)}", s(R.string.format_quote)),
        Format(listed("- ", s(R.string.format_list_words)), s(R.string.format_list)),
        Format(numbered(s(R.string.format_numbered_words)), s(R.string.format_numbered)),
        Format("[${s(R.string.format_link_word)}](https://example.org)", s(R.string.format_link)),
        Format("## ${s(R.string.format_heading_word)}", s(R.string.format_heading), shown = false),
        Format("| a | b |\n| - | - |\n| 1 | 2 |", s(R.string.format_table)),
        Format(":smile:", s(R.string.format_emoji)),
        Format("\\*${s(R.string.format_escape_word)}\\*", s(R.string.format_escape)),
    )
}

private fun listed(
    sign: String,
    lines: String,
) = lines.lines().joinToString("\n") { sign + it }

private fun numbered(lines: String) =
    lines.lines().mapIndexed { i, line -> "${i + 1}. $line" }.joinToString("\n")
