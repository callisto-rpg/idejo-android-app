// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.idejo.app.core.designsystem.component.Avatar
import org.idejo.app.core.designsystem.component.StatusBubble
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.designsystem.theme.parseHexColor
import org.idejo.app.core.designsystem.theme.pictureUrl
import org.idejo.app.core.model.Me

/**
 * The box's value, following the machine's text too (the server's answer, CO-09), a caret at
 * the end staying there.
 */
@Composable
internal fun rememberFollowing(text: String): MutableState<TextFieldValue> {
    val value = remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    val current = value.value
    if (current.text != text) {
        val atEnd = current.selection.collapsed && current.selection.start == current.text.length
        val caret = if (atEnd) text.length else current.selection.start.coerceAtMost(text.length)
        value.value = TextFieldValue(text, TextRange(caret))
    }
    return value
}

@Composable
internal fun textColors(glow: Float) =
    TextColors(
        sent = MaterialTheme.colorScheme.onSurface,
        unsent = MaterialTheme.colorScheme.onSurfaceVariant,
        highlight = IdejoTheme.colors.unsentHighlight,
        glow = lerp(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.primary, glow),
        link = MaterialTheme.colorScheme.primary,
        faded = MaterialTheme.colorScheme.onSurfaceVariant,
        code = MaterialTheme.colorScheme.surfaceVariant,
        marked = MaterialTheme.colorScheme.tertiaryContainer,
        title = IdejoTheme.typography.titleLarge.fontSize,
    )

/**
 * Hardware keys (CO-03): Enter sends, Shift+Enter is a line break ([newLine]), Ctrl+Enter
 * (Cmd+Enter, and Search+Enter on Chromebooks, which reports Meta) completes.
 */
internal fun keys(
    event: KeyEvent,
    events: ComposerEvents,
    newLine: () -> Unit,
): Boolean {
    if (event.key != Key.Enter && event.key != Key.NumPadEnter) return false
    if (event.type == KeyEventType.KeyDown) {
        when {
            event.isCtrlPressed || event.isMetaPressed -> events.onComplete()
            event.isShiftPressed -> newLine()
            else -> events.onEnter()
        }
    }
    return true
}

internal fun TextFieldValue.insert(text: String): TextFieldValue {
    val start = selection.min
    val next = this.text.replaceRange(start, selection.max, text)
    return TextFieldValue(next, TextRange(start + text.length))
}

/** Text just sent glows, then settles (CO-04): 1 at once, 0 after [GLOW_MILLIS]. */
@Composable
internal fun rememberGlow(range: IntRange?): Float {
    val glow = remember { Animatable(0f) }
    val reduced = IdejoTheme.reducedMotion
    LaunchedEffect(range) {
        if (range == null || reduced) {
            glow.snapTo(0f)
        } else {
            glow.snapTo(1f)
            glow.animateTo(0f, tween(GLOW_MILLIS))
        }
    }
    return glow.value
}

internal data class TextColors(
    val sent: Color,
    val unsent: Color,
    val highlight: Color,
    val glow: Color,
    val link: Color = sent,
    val faded: Color = unsent,
    val code: Color = highlight,
    val marked: Color = highlight,
    val title: TextUnit = TextUnit.Unspecified,
)

/** How [mark] looks in the box (CO-11). */
internal fun TextColors.style(mark: Mark): SpanStyle =
    when (mark) {
        Mark.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
        Mark.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
        Mark.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        Mark.Highlight -> SpanStyle(background = marked)
        Mark.Code -> SpanStyle(fontFamily = FontFamily.Monospace, background = code)
        Mark.Spoiler -> SpanStyle(color = faded)
        Mark.Link -> SpanStyle(color = link, textDecoration = TextDecoration.Underline)
        Mark.Title -> SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = title)
        Mark.Quote -> SpanStyle(color = faded, fontStyle = FontStyle.Italic)
        Mark.Image -> SpanStyle(background = code, color = link)
    }

/**
 * The box's text as it will look (CO-11), sent text plain and unsent highlighted (CO-04);
 * with the [graphical] composer, finished Markdown signs hidden.
 */
internal class SentText(
    private val state: ComposerState,
    private val colors: TextColors,
    private val graphical: Boolean = false,
    private val imageLabel: String = "image: %1\$s",
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val look = markdownLook(text.text, graphical) { imageLabel.format(it) }
        val swapped = Swapped(text.text, look.swaps)
        val unsent = unsentRange(state.sent, text.text)
        val styled =
            buildAnnotatedString {
                append(swapped.shown)
                val style = { span: SpanStyle, start: Int, end: Int ->
                    val from = swapped.shown(start)
                    val to = swapped.shown(end)
                    if (to > from) addStyle(span, from, to)
                }
                look.styles.forEach { style(colors.style(it.mark), it.start, it.end) }
                state.justSent?.let { range ->
                    if (range.last < text.length) {
                        style(SpanStyle(color = colors.glow), range.first, range.last + 1)
                    }
                }
                if (unsent != null && !unsent.isEmpty()) {
                    style(
                        SpanStyle(color = colors.unsent, background = colors.highlight),
                        unsent.first,
                        unsent.last + 1,
                    )
                }
            }
        val mapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = swapped.shown(offset)

                override fun transformedToOriginal(offset: Int) = swapped.text(offset)
            }
        return TransformedText(styled, mapping)
    }

    override fun equals(other: Any?) =
        other is SentText &&
            other.state == state &&
            other.colors == colors &&
            other.graphical == graphical &&
            other.imageLabel == imageLabel

    override fun hashCode() =
        (state.hashCode() * HASH + colors.hashCode()) * HASH + graphical.hashCode()
}

private const val GLOW_MILLIS = 1_000
private const val HASH = 31
