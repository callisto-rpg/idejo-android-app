// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** What a dot before a topic says, from the weakest to the strongest (SH-32). */
enum class DotKind {
    /** New messages: grey. */
    NewMessages,

    /** One of the member's `+roles` was mentioned: blue. */
    RoleMention,

    /** A reply to the member: orange. */
    Reply,

    /** The member was mentioned: red. */
    Mention,
}

/** What an in-app bubble is about (NT-01). */
enum class BubbleKind {
    /** A new message in a followed topic: grey. */
    Followed,

    /** One of the member's `+roles` mentioned: blue. */
    RoleMention,

    /** A reply to the member: orange. */
    Reply,

    /** The member mentioned: red. */
    Mention,

    /** A reaction to the member's message: purple. */
    Reaction,
}

/**
 * idejo's colours beyond Material's roles (CLAUDE.md → Conventions): the dots, the bubbles,
 * slow mode and the composer's unsent text, plus role and category colours made readable on
 * this forum's backgrounds (TH-05). Read them through `IdejoTheme.colors`.
 */
@Immutable
data class IdejoColors(
    val dotNewMessages: Color,
    val dotRoleMention: Color,
    val dotReply: Color,
    val dotMention: Color,
    val bubbleReaction: Color,
    val slowMode: Color,
    val slowModeContainer: Color,
    val onSlowModeContainer: Color,
    val unsentHighlight: Color,
    private val backgrounds: List<Color>,
    private val fallbackName: Color,
) {
    fun dot(kind: DotKind): Color =
        when (kind) {
            DotKind.NewMessages -> dotNewMessages
            DotKind.RoleMention -> dotRoleMention
            DotKind.Reply -> dotReply
            DotKind.Mention -> dotMention
        }

    fun bubble(kind: BubbleKind): Color =
        when (kind) {
            BubbleKind.Followed -> dotNewMessages
            BubbleKind.RoleMention -> dotRoleMention
            BubbleKind.Reply -> dotReply
            BubbleKind.Mention -> dotMention
            BubbleKind.Reaction -> bubbleReaction
        }

    /**
     * A display name in its role's colour (TH-05, TV-02), nudged until it reads at 4.5:1 on
     * the page and its cards; without a role colour, the page's text colour.
     */
    fun roleName(roleColor: Color?): Color =
        roleColor?.let { readableOn(it, backgrounds) } ?: fallbackName

    /** A role colour from the API (`#rrggbb`), readable here; see [roleName]. */
    fun roleName(roleHex: String?): Color = roleName(parseHexColor(roleHex))

    /** A category's colour from the API (`colour`), or the forum's secondary (TH-05). */
    fun category(
        hex: String?,
        scheme: ColorScheme,
    ): Color = parseHexColor(hex)?.copy(alpha = 1f) ?: scheme.secondary
}

/** idejo's extras for a scheme made by [colorScheme]. */
fun idejoColors(
    scheme: ColorScheme,
    dark: Boolean,
): IdejoColors =
    IdejoColors(
        dotNewMessages = Color(0xFF8A8A8A),
        dotRoleMention = Color(0xFF2F6FDC),
        dotReply = Color(0xFFE07A10),
        dotMention = Color(0xFFD43C3C),
        bubbleReaction = Color(0xFF8A4FC0),
        slowMode = Color(0xFFE07A10),
        slowModeContainer = if (dark) Color(0xFF4A2C10) else Color(0xFFFBE8D4),
        onSlowModeContainer = if (dark) Color(0xFFFFD3A6) else Color(0xFF8A4300),
        unsentHighlight = scheme.primary.copy(alpha = if (dark) UNSENT_DARK else UNSENT_LIGHT),
        backgrounds = listOf(scheme.background, scheme.surface, scheme.surfaceContainerHighest),
        fallbackName = scheme.onBackground,
    )

val LocalIdejoColors =
    staticCompositionLocalOf { idejoColors(ForumTheme.Default.colorScheme(false), false) }

private const val UNSENT_LIGHT = 0.14f
private const val UNSENT_DARK = 0.2f
