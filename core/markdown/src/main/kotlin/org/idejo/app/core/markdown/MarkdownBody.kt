// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Emoji
import org.idejo.app.core.model.Mention

/**
 * What a message body does when tapped, and how it finds what the Markdown names. The
 * caller resolves addresses against the forum and opens them (links, TV-22's cards).
 */
@Immutable
data class MarkdownActions(
    /** A link written in the Markdown, as written (`/m/…`, `https://…`). */
    val onLink: (String) -> Unit = {},
    /** An inline link idejo resolved (`@user`, `#topic`…): the web's small card (TV-22). */
    val onInlineLink: (Mention) -> Unit = {},
    /** A hidden `||spoiler||` tapped, by its number (TV-23). */
    val onReveal: (Int) -> Unit = {},
    /** The forum's emoji by name (`GET /server` → `emoji`, and Unicode's), or null. */
    val emoji: (String) -> Emoji? = { null },
)

/**
 * A message body drawn natively from its Markdown (TV-20 to TV-24). [revealed] holds the
 * inline spoilers shown. [image] draws an image from its address (TV-25: sized, lazy, tap
 * to zoom, from the message's `images`); [emojiImage] a server emoji from its address.
 * [pulse] makes the tags naming the reader pulse while their message glows (TV-07).
 */
@Composable
fun MarkdownBody(
    document: MarkdownDocument,
    modifier: Modifier = Modifier,
    actions: MarkdownActions = MarkdownActions(),
    revealed: Set<Int> = emptySet(),
    pulse: MentionPulse? = null,
    style: TextStyle = IdejoTheme.typography.bodyLarge,
    image: @Composable (MdInline.Image) -> Unit = { ImageWords(it, style) },
    emojiImage: @Composable (Emoji) -> Unit = {},
) {
    val scope =
        BodyScope(
            colors = inlineColors(),
            actions = actions,
            revealed = revealed,
            pulse = pulse,
            style = style,
            spoilerWord = stringResource(R.string.markdown_spoiler),
            pictures = Pictures(image, emojiImage),
        )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(BLOCK_GAP)) {
        document.blocks.forEach { scope.Block(it) }
    }
}

@Composable
private fun inlineColors(): InlineColors {
    val scheme = MaterialTheme.colorScheme
    val extras = IdejoTheme.colors
    return remember(scheme, extras) {
        InlineColors(
            link = scheme.primary,
            code = scheme.surfaceVariant,
            highlight = scheme.primary.copy(alpha = HIGHLIGHT),
            spoilerBar = scheme.onSurfaceVariant,
            spoilerOpen = scheme.onSurface.copy(alpha = SPOILER_OPEN),
            linkText = extras::linkText,
            linkHue = extras::linkHue,
        )
    }
}

@Suppress("LongParameterList") // What every block of one body shares.
private class BodyScope(
    val colors: InlineColors,
    val actions: MarkdownActions,
    val revealed: Set<Int>,
    val pulse: MentionPulse?,
    val style: TextStyle,
    val spoilerWord: String,
    val pictures: Pictures,
) {
    @Composable
    fun Block(
        block: MdBlock,
        modifier: Modifier = Modifier,
    ) {
        Box(modifier) { Content(block) }
    }

    @Composable
    private fun Content(block: MdBlock) {
        when (block) {
            is MdBlock.Paragraph -> {
                Paragraph(block.inlines, style)
            }

            is MdBlock.Heading -> {
                Paragraph(
                    block.inlines,
                    headingStyle(block.level),
                    Modifier.semantics { heading() },
                )
            }

            is MdBlock.Small -> {
                Paragraph(
                    block.inlines,
                    IdejoTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }

            is MdBlock.Quote -> {
                Quote(block.blocks)
            }

            is MdBlock.ListBlock -> {
                ListBlock(block)
            }

            is MdBlock.Code -> {
                CodeBlock(block.text)
            }

            MdBlock.Rule -> {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }

            is MdBlock.Table -> {
                Table(block)
            }
        }
    }

    /** A run of text; images in it stand as blocks of their own between its words. */
    @Composable
    fun Paragraph(
        inlines: List<MdInline>,
        textStyle: TextStyle,
        modifier: Modifier = Modifier,
        align: TextAlign? = null,
    ) {
        val parts = inlines.splitImages()
        val only = parts.singleOrNull()
        if (only is Part.Words) {
            Run(only.inlines, textStyle, modifier, align)
            return
        }
        Column(modifier, verticalArrangement = Arrangement.spacedBy(BLOCK_GAP)) {
            for (part in parts) {
                when (part) {
                    is Part.Picture -> pictures.image(part.image)
                    is Part.Words -> Run(part.inlines, textStyle, Modifier, align)
                }
            }
        }
    }

    @Composable
    private fun Run(
        inlines: List<MdInline>,
        textStyle: TextStyle,
        modifier: Modifier = Modifier,
        align: TextAlign? = null,
    ) {
        val builder = InlineBuilder(colors, actions, revealed, spoilerWord, pulse)
        val text = builder.build(inlines)
        val content =
            builder.images.mapValues { (_, emoji) ->
                InlineTextContent(
                    Placeholder(EMOJI_SIZE.em, EMOJI_SIZE.em, PlaceholderVerticalAlign.TextCenter),
                ) { pictures.emoji(emoji) }
            }
        val spoken = builder.spoken
        Text(
            text,
            modifier.then(
                if (builder.hidesSpoiler) {
                    Modifier.semantics { contentDescription = spoken }
                } else {
                    Modifier
                },
            ),
            style = if (align != null) textStyle.copy(textAlign = align) else textStyle,
            color = textStyle.color.takeOrElse { LocalContentColor.current },
            inlineContent = content,
        )
    }

    @Composable
    private fun Quote(blocks: List<MdBlock>) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(QUOTE_BAR)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Column(
                Modifier.padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(BLOCK_GAP),
            ) { blocks.forEach { Block(it) } }
        }
    }

    @Composable
    private fun ListBlock(list: MdBlock.ListBlock) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            list.items.forEachIndexed { index, item ->
                Row {
                    val marker =
                        when {
                            item.checked == true -> CHECKED
                            item.checked == false -> UNCHECKED
                            list.ordered -> "${list.start + index}."
                            else -> BULLET
                        }
                    val described =
                        when (item.checked) {
                            true -> stringResource(R.string.markdown_task_done)
                            false -> stringResource(R.string.markdown_task_open)
                            null -> null
                        }
                    Text(
                        marker,
                        Modifier
                            .widthIn(min = MARKER_WIDTH)
                            .padding(end = 6.dp)
                            .then(
                                if (described != null) {
                                    Modifier.semantics { contentDescription = described }
                                } else {
                                    Modifier
                                },
                            ),
                        style = style,
                        textAlign = TextAlign.End,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.blocks.forEach { Block(it) }
                    }
                }
            }
        }
    }

    @Composable
    private fun CodeBlock(text: String) {
        Surface(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text,
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
                style =
                    style.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize =
                            style.fontSize * CODE,
                    ),
                softWrap = false,
            )
        }
    }

    @Composable
    private fun Table(table: MdBlock.Table) {
        val columns = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0)
        if (columns == 0) return
        val rows = listOf(table.header) + table.rows
        val line = MaterialTheme.colorScheme.outlineVariant
        Column {
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                TableLayout(columns) {
                    rows.forEachIndexed { rowIndex, row ->
                        repeat(columns) { column ->
                            val cell = row.getOrNull(column)
                            Box(
                                Modifier
                                    .background(
                                        if (rowIndex == 0) {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        } else {
                                            Color.Transparent
                                        },
                                    ).padding(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                if (cell != null) {
                                    Paragraph(
                                        cell.inlines,
                                        if (rowIndex == 0) {
                                            style.copy(fontWeight = FontWeight.SemiBold)
                                        } else {
                                            style
                                        },
                                        Modifier.fillMaxWidth(),
                                        align = cell.alignment.textAlign,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = line)
        }
    }
}

/** A grid whose columns are as wide as their widest cell, at most [MAX_CELL]. */
@Composable
private fun TableLayout(
    columns: Int,
    content: @Composable () -> Unit,
) {
    Layout(content) { measurables, _ ->
        val max = MAX_CELL.roundToPx()
        val widths = IntArray(columns)
        measurables.forEachIndexed { i, m ->
            val w = m.maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(max)
            widths[i % columns] = maxOf(widths[i % columns], w)
        }
        val placeables =
            measurables.mapIndexed { i, m ->
                val w = widths[i % columns]
                m.measure(Constraints(minWidth = w, maxWidth = w))
            }
        val rows = placeables.chunked(columns)
        val heights = rows.map { row -> row.maxOf { it.height } }
        layout(widths.sum(), heights.sum()) {
            var y = 0
            rows.forEachIndexed { r, row ->
                var x = 0
                row.forEachIndexed { c, p ->
                    p.place(x, y)
                    x += widths[c]
                }
                y += heights[r]
            }
        }
    }
}

/** Without a way to draw images, an image shows its words. */
@Composable
private fun ImageWords(
    image: MdInline.Image,
    style: TextStyle,
) {
    Text(image.alt, style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
}

/** How images and server emoji are drawn, from the caller. */
private class Pictures(
    val image: @Composable (MdInline.Image) -> Unit,
    val emoji: @Composable (Emoji) -> Unit,
)

@Suppress("MagicNumber") // Heading levels.
@Composable
private fun headingStyle(level: Int): TextStyle =
    when (level) {
        1 -> IdejoTheme.typography.headlineSmall
        2 -> IdejoTheme.typography.titleLarge
        3 -> IdejoTheme.typography.titleMedium
        else -> IdejoTheme.typography.titleSmall
    }

/** A paragraph's runs of words, and the images standing between them. */
private sealed interface Part {
    data class Words(
        val inlines: List<MdInline>,
    ) : Part

    data class Picture(
        val image: MdInline.Image,
    ) : Part
}

private fun List<MdInline>.splitImages(): List<Part> {
    val parts = mutableListOf<Part>()
    var run = mutableListOf<MdInline>()
    val flush = {
        if (run.any { !it.isSpace() }) parts += Part.Words(run)
        run = mutableListOf()
    }
    for (inline in this) {
        if (inline is MdInline.Image) {
            flush()
            parts += Part.Picture(inline)
        } else {
            run += inline
        }
    }
    flush()
    return parts.ifEmpty { listOf(Part.Words(emptyList())) }
}

private fun MdInline.isSpace(): Boolean =
    this is MdInline.Break || (this is MdInline.Text && text.isBlank())

private val MdBlock.Alignment.textAlign: TextAlign
    get() =
        when (this) {
            MdBlock.Alignment.Start -> TextAlign.Start
            MdBlock.Alignment.Center -> TextAlign.Center
            MdBlock.Alignment.End -> TextAlign.End
        }

private val BLOCK_GAP = 8.dp
private val QUOTE_BAR = 3.dp
private val MARKER_WIDTH = 20.dp
private val MAX_CELL = 280.dp
private const val EMOJI_SIZE = 1.25f
private const val CODE = 0.9f
private const val HIGHLIGHT = 0.3f
private const val SPOILER_OPEN = 0.08f
private const val BULLET = "•"
private const val CHECKED = "☑"
private const val UNCHECKED = "☐"
