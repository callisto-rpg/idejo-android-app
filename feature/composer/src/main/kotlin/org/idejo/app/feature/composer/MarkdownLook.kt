// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

/** How a part of the box's text looks (CO-11). */
internal enum class Mark {
    Bold,
    Italic,
    Strike,
    Highlight,
    Code,
    Spoiler,
    Link,
    Title,
    Quote,
    Image,
}

/** [mark] on the box's characters [start] until [end]. */
internal data class Styled(
    val mark: Mark,
    val start: Int,
    val end: Int,
)

/** Characters [start] until [end] shown as [with] (a sign hidden, `- ` as a bullet). */
internal data class Swap(
    val start: Int,
    val end: Int,
    val with: String = "",
)

internal data class Look(
    val styles: List<Styled>,
    val swaps: List<Swap>,
)

/**
 * The box's Markdown as it will look (CO-11). With the graphical composer, finished signs
 * disappear and `- ` becomes a bullet; without it, the signs stay and only the text is styled.
 */
internal fun markdownLook(
    text: String,
    graphical: Boolean,
    imageLabel: (String) -> String = { "image: $it" },
): Look {
    val styles = mutableListOf<Styled>()
    val swaps = mutableListOf<Swap>()
    var lineStart = 0
    for (line in text.split('\n')) {
        val body = block(line, lineStart, styles, swaps)
        inline(text, lineStart + body, lineStart + line.length, styles, swaps)
        lineStart += line.length + 1
    }
    // An uploaded image shows as the web's "image:" pill, with or without the graphical
    // composer (CO-14).
    val images = mutableListOf<Swap>()
    for (found in IMAGE.findAll(text)) {
        val start = found.range.first
        val end = found.range.last + 1
        images += Swap(start, end, imageLabel(found.groupValues[1].ifBlank { "image" }))
        styles.removeAll { it.start >= start && it.end <= end }
        styles += Styled(Mark.Image, start, end)
    }
    val kept =
        if (graphical) {
            swaps.filter { s ->
                images.none { s.start < it.end && it.start < s.end }
            }
        } else {
            emptyList()
        }
    return Look(styles, clean(kept + images))
}

/** A line's block sign; gives where its text starts. */
private fun block(
    line: String,
    at: Int,
    styles: MutableList<Styled>,
    swaps: MutableList<Swap>,
): Int {
    val sign = BLOCK.find(line)?.value ?: return 0
    val signs = sign.trimStart()
    val indent = sign.length - signs.length
    when (signs.first()) {
        '#' -> styles += Styled(Mark.Title, at, at + line.length)
        '>' -> styles += Styled(Mark.Quote, at, at + line.length)
    }
    when (signs.first()) {
        '#', '>' -> swaps += Swap(at, at + sign.length)
        '-', '*' -> swaps += Swap(at + indent, at + sign.length, "• ")
    }
    return sign.length
}

private fun inline(
    text: String,
    from: Int,
    to: Int,
    styles: MutableList<Styled>,
    swaps: MutableList<Swap>,
) {
    val part = text.substring(from, to)
    val code = CODE.findAll(part).map { it.range }.toList()
    code.forEach { styles += sign(from + it.first, from + it.last + 1, 1, Mark.Code, swaps) }
    for ((pattern, mark) in WRAPS) {
        for (found in pattern.findAll(part)) {
            val range = found.groups[1]!!.range
            if (code.any { range.first in it || range.last in it }) continue
            val signs = range.first - found.range.first
            styles +=
                sign(from + found.range.first, from + found.range.last + 1, signs, mark, swaps)
        }
    }
    for (found in LINK.findAll(part)) {
        if (code.any { found.range.first in it }) continue
        val label = found.groups[1]!!.range
        val start = from + found.range.first
        swaps += Swap(start, start + 1)
        swaps += Swap(from + label.last + 1, from + found.range.last + 1)
        styles += Styled(Mark.Link, from + label.first, from + label.last + 1)
    }
}

/** A span between signs [signs] long each side: styled, its signs hidden. */
private fun sign(
    start: Int,
    end: Int,
    signs: Int,
    mark: Mark,
    swaps: MutableList<Swap>,
): Styled {
    swaps += Swap(start, start + signs)
    swaps += Swap(end - signs, end)
    return Styled(mark, start + signs, end - signs)
}

/** In order, the overlapping ones left out. */
private fun clean(swaps: List<Swap>): List<Swap> {
    val kept = mutableListOf<Swap>()
    for (swap in swaps.sortedBy { it.start }) {
        if (kept.lastOrNull()?.let { it.end > swap.start } != true) kept += swap
    }
    return kept
}

/** The box's text with [swaps] made, and where each offset goes either way. */
internal class Swapped(
    text: String,
    swaps: List<Swap>,
) {
    val shown: String
    private val toShown = IntArray(text.length + 1)
    private val toText: IntArray

    init {
        val out = StringBuilder()
        // back[p]: the text's offset for shown offset p, the last one that reaches it.
        val back = mutableListOf<Int>()
        val put = { at: Int, offset: Int ->
            if (at ==
                back.size
            ) {
                back += offset
            } else {
                back[at] = offset
            }
        }
        var i = 0
        var next = 0
        while (i <= text.length) {
            val swap = swaps.getOrNull(next)
            if (swap != null && swap.start == i) {
                for (k in swap.start until swap.end) toShown[k] = out.length
                swap.with.forEach {
                    put(out.length, swap.end)
                    out.append(it)
                }
                i = swap.end
                next++
            } else {
                toShown[i] = out.length
                put(out.length, i)
                if (i < text.length) out.append(text[i])
                i++
            }
        }
        shown = out.toString()
        toText = back.toIntArray()
    }

    fun shown(offset: Int) = toShown[offset.coerceIn(0, toShown.lastIndex)]

    fun text(offset: Int) = toText[offset.coerceIn(0, toText.lastIndex)]
}

private val BLOCK = Regex("""^(#{1,3} |> ?|\s*[-*] |\s*\d+\. )""")
private val IMAGE = Regex("""!\[([^\]\n]*)]\((/uploads/[^)\s]+)\)""")
private val CODE = Regex("""`[^`\n]+`""")
private val LINK = Regex("""\[([^\]\n]+)]\([^)\s]+\)""")
private val WRAPS =
    listOf(
        Regex("""\*\*(\S(?:.*?\S)?)\*\*""") to Mark.Bold,
        Regex("""~~(\S(?:.*?\S)?)~~""") to Mark.Strike,
        Regex("""==(\S(?:.*?\S)?)==""") to Mark.Highlight,
        Regex("""\|\|(\S(?:.*?\S)?)\|\|""") to Mark.Spoiler,
        Regex("""(?<![*\w])\*(?!\*)(\S(?:[^*]*?\S)?)\*(?!\*)""") to Mark.Italic,
        Regex("""(?<!\w)_(\S(?:[^_]*?\S)?)_(?!\w)""") to Mark.Italic,
    )
