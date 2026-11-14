// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.markdown

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import org.idejo.app.core.designsystem.theme.LinkKind
import org.idejo.app.core.model.Emoji
import org.idejo.app.core.model.Mention

/** The colours inline runs are drawn with, read from the theme once per body. */
internal data class InlineColors(
    val link: Color,
    val code: Color,
    val highlight: Color,
    val spoilerBar: Color,
    val spoilerOpen: Color,
    val linkText: (LinkKind) -> Color,
    val linkHue: (LinkKind) -> Color,
)

/** An inline image (a server emoji) to draw in a run, by its placeholder's id. */
internal const val EMOJI_PREFIX = "emoji:"

/** A paragraph's runs as one annotated string, and what TalkBack says for it. */
internal class InlineBuilder(
    private val colors: InlineColors,
    private val actions: MarkdownActions,
    private val revealed: Set<Int>,
    private val spoilerWord: String,
) {
    /** The images (server emoji) the string holds, to give `Text` as inline content. */
    val images = mutableMapOf<String, Emoji>()

    /** True once a hidden spoiler was drawn: then [spoken] stands for the text. */
    var hidesSpoiler = false
        private set

    private val speech = StringBuilder()

    val spoken: String get() = speech.toString()

    fun build(inlines: List<MdInline>): AnnotatedString =
        buildAnnotatedString {
            inlines.forEach { append(it) }
        }

    @Suppress("CyclomaticComplexMethod", "LongMethod") // One branch per kind of inline.
    private fun AnnotatedString.Builder.append(inline: MdInline) {
        when (inline) {
            is MdInline.Text -> {
                text(inline.text)
            }

            is MdInline.Break -> {
                text(if (inline.hard) "\n" else " ")
            }

            is MdInline.Emphasis -> {
                styled(SpanStyle(fontStyle = FontStyle.Italic), inline.children)
            }

            is MdInline.Strong -> {
                styled(SpanStyle(fontWeight = FontWeight.Bold), inline.children)
            }

            is MdInline.Strike -> {
                styled(SpanStyle(textDecoration = TextDecoration.LineThrough), inline.children)
            }

            is MdInline.Highlight -> {
                styled(SpanStyle(background = colors.highlight), inline.children)
            }

            is MdInline.Superscript -> {
                styled(
                    SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = SMALLER.em),
                    inline.children,
                )
            }

            is MdInline.Code -> {
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colors.code))
                text(inline.text)
                pop()
            }

            is MdInline.Link -> {
                val url = inline.url
                pushLink(
                    LinkAnnotation.Clickable(
                        url,
                        TextLinkStyles(
                            SpanStyle(
                                color = colors.link,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                    ) { actions.onLink(url) },
                )
                inline.children.forEach { append(it) }
                pop()
            }

            is MdInline.InlineLink -> {
                inlineLink(inline)
            }

            is MdInline.Image -> {
                // Images stand as blocks of their own (Blocks.kt); one inside a run of
                // text keeps its words.
                text(inline.alt)
            }

            is MdInline.Emoji -> {
                emoji(inline.name)
            }

            is MdInline.Spoiler -> {
                spoiler(inline)
            }
        }
    }

    private fun AnnotatedString.Builder.text(value: String) {
        append(value)
        speech.append(value)
    }

    private fun AnnotatedString.Builder.styled(
        style: SpanStyle,
        children: List<MdInline>,
    ) {
        pushStyle(style)
        children.forEach { append(it) }
        pop()
    }

    private fun AnnotatedString.Builder.inlineLink(link: MdInline.InlineLink) {
        val kind = link.mention.kind
        if (kind == null) {
            text(link.text)
            return
        }
        val hue = colors.linkHue(kind)
        val style =
            SpanStyle(
                color = colors.linkText(kind),
                background = hue.copy(alpha = TINT),
                fontWeight = FontWeight.SemiBold,
            )
        val mention = link.mention
        pushLink(
            LinkAnnotation.Clickable(
                "${mention.type}:${mention.id ?: mention.slug}",
                TextLinkStyles(style, pressedStyle = SpanStyle(background = hue.copy(PRESSED))),
            ) { actions.onInlineLink(mention) },
        )
        // A thin space each side, as the web's padding.
        text(" ${link.text} ")
        pop()
    }

    private fun AnnotatedString.Builder.emoji(name: String) {
        val found = actions.emoji(name)
        when {
            found?.char != null -> {
                append(found.char!!)
                speech.append(found.char)
            }

            found?.imageUrl != null -> {
                val id = EMOJI_PREFIX + name
                images[id] = found
                appendInlineContent(id, ":$name:")
                speech.append(name)
            }

            else -> {
                text(":$name:")
            }
        }
    }

    private fun AnnotatedString.Builder.spoiler(spoiler: MdInline.Spoiler) {
        if (spoiler.index in revealed) {
            styled(SpanStyle(background = colors.spoilerOpen), spoiler.children)
            return
        }
        hidesSpoiler = true
        // Hidden: a bar the size of its words, which neither show nor are read (TV-23).
        val index = spoiler.index
        pushLink(
            LinkAnnotation.Clickable(
                "spoiler:$index",
                TextLinkStyles(
                    SpanStyle(color = colors.spoilerBar, background = colors.spoilerBar),
                ),
            ) { actions.onReveal(index) },
        )
        append(spoiler.children.plain())
        pop()
        speech.append(spoilerWord)
    }

    private companion object {
        const val SMALLER = 0.75f
        const val TINT = 0.10f
        const val PRESSED = 0.22f
    }
}

/** The kind of link an inline link is, or null for a kind this app does not know. */
internal val Mention.kind: LinkKind?
    get() =
        when (type) {
            "user" -> LinkKind.User
            "role" -> LinkKind.Role
            "topic" -> LinkKind.Topic
            "category" -> LinkKind.Category
            "tag" -> LinkKind.Tag
            "forum" -> LinkKind.Forum
            else -> null
        }

/** The words of [this], without their styles. */
internal fun List<MdInline>.plain(): String =
    joinToString("") { inline ->
        when (inline) {
            is MdInline.Text -> inline.text
            is MdInline.Code -> inline.text
            is MdInline.Break -> if (inline.hard) "\n" else " "
            is MdInline.InlineLink -> inline.text
            is MdInline.Image -> inline.alt
            is MdInline.Emoji -> ":${inline.name}:"
            else -> inline.children().plain()
        }
    }
