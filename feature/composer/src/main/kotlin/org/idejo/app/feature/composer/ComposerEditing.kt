// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** The pencil's controls (CO-10), and what Shift+Enter and Backspace do in lists (CO-11). */
internal enum class Format(
    val sign: String,
) {
    Bold("**"),
    Italic("*"),
    Strike("~~"),
    Code("`"),
}

/** The selection between [Format.sign]s, or the signs around the caret. */
internal fun TextFieldValue.wrap(format: Format): TextFieldValue {
    val sign = format.sign
    val start = selection.min
    val end = selection.max
    val next =
        text.substring(0, start) + sign + text.substring(start, end) + sign +
            text.substring(end)
    return TextFieldValue(next, TextRange(start + sign.length, end + sign.length))
}

/** **Insert link**: `[words](https://)`, the address selected to type over. */
internal fun TextFieldValue.link(): TextFieldValue {
    val start = selection.min
    val words = text.substring(start, selection.max)
    val inserted = "[$words](https://)"
    val next = text.replaceRange(start, selection.max, inserted)
    val address = start + "[$words](".length
    return TextFieldValue(next, TextRange(address, address + "https://".length))
}

/** **Quote**, **Bulleted list**, **Numbered list**: each line of the selection gets its sign. */
internal fun TextFieldValue.lines(sign: (Int) -> String): TextFieldValue {
    val from = text.lastIndexOf('\n', selection.min - 1) + 1
    val to = text.indexOf('\n', selection.max).let { if (it < 0) text.length else it }
    val lines = text.substring(from, to).split('\n')
    val signed = lines.mapIndexed { i, line -> sign(i + 1) + line }.joinToString("\n")
    val next = text.substring(0, from) + signed + text.substring(to)
    return TextFieldValue(next, TextRange(from + signed.length))
}

/**
 * **New line** (Shift+Enter): in a list it starts the next item; on an empty item it ends
 * the list, as the web's.
 */
internal fun TextFieldValue.newLine(): TextFieldValue {
    val caret = selection.min
    val from = text.lastIndexOf('\n', caret - 1) + 1
    val sign = ITEM.find(text.substring(from, caret))?.value
    val to = text.indexOf('\n', caret).let { if (it < 0) text.length else it }
    return when {
        sign == null -> {
            insert("\n")
        }

        text.substring(from, to) == sign -> {
            TextFieldValue(text.removeRange(from, from + sign.length), TextRange(from))
        }

        else -> {
            insert("\n" + nextSign(sign))
        }
    }
}

private fun nextSign(sign: String): String {
    val number = sign.trim().removeSuffix(".").toIntOrNull() ?: return sign
    return sign.replace("$number.", "${number + 1}.")
}

/**
 * Backspace at the start of an item removes its bullet (CO-11): [next] is the box after one
 * character was taken out of [before]; null when it is not that.
 */
internal fun bulletRemoved(
    before: TextFieldValue,
    next: TextFieldValue,
): TextFieldValue? {
    val caret = before.selection.min
    val deleted = before.text.length - next.text.length == 1 && next.selection.min == caret - 1
    if (!before.selection.collapsed || !deleted) return null
    val from = before.text.lastIndexOf('\n', caret - 1) + 1
    val sign = ITEM.find(before.text.substring(from, caret))?.value ?: return null
    if (from + sign.length != caret) return null
    return TextFieldValue(before.text.removeRange(from, caret), TextRange(from))
}

/** What follows a sign `@ + # / $ &` before the caret (CO-12). */
internal data class SuggestAt(
    val sign: Char,
    val query: String,
    val start: Int,
)

internal fun TextFieldValue.suggestAt(): SuggestAt? {
    if (!selection.collapsed) return null
    val caret = selection.start
    var at = caret
    while (at > 0 && (text[at - 1].isLetterOrDigit() || text[at - 1] in WORD)) at--
    val sign = text.getOrNull(at - 1)?.takeIf { it in SIGNS } ?: return null
    val before = text.getOrNull(at - 2)
    if (before != null && !before.isWhitespace() && before != '(') return null
    return SuggestAt(sign, text.substring(at, caret), at - 1)
}

/** The suggestion [insert] chosen: the sign, its name and a space. */
internal fun TextFieldValue.accept(
    at: SuggestAt,
    insert: String,
): TextFieldValue {
    val chosen = "${at.sign}$insert "
    val next = text.replaceRange(at.start, selection.start, chosen)
    return TextFieldValue(next, TextRange(at.start + chosen.length))
}

private const val SIGNS = "@+#/$&"
private const val WORD = "-_."
private val ITEM = Regex("""^\s*(?:[-*]|\d+\.) """)
