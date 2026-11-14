// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.idejo.app.core.designsystem.theme.BubbleKind
import org.idejo.app.core.designsystem.theme.DotKind
import org.idejo.app.core.designsystem.theme.ForumTheme
import org.idejo.app.core.designsystem.theme.IdejoTheme

// The components as the design canvas shows them, for previews and screenshot tests.

internal object SampleThemes {
    val Garden =
        ForumTheme.fromHex("#3f7d3a", "#8a6a1f", "#2c6a8e", "#f7faf2", "#0b1410")
    val Tennis =
        ForumTheme.fromHex("#4fa3e0", "#c2410c", "#1f5f8b", "#f4f8fc", "#0b1118")
}

private val Moderator = Color(0xFF6A3E8E)
private val Vegetables = Color(0xFF8A6A1F)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun TopBarSample() {
    TopBar(
        crumbs =
            listOf(
                Crumb.Forum("Garden club", onClick = {}),
                Crumb.Category("Vegetables", Vegetables, onClick = {}),
                Crumb.Page("Tomato plans for spring"),
            ),
        onSidebarClick = {},
        unread = Unread(3, DotKind.Reply) {},
        account = {
            Avatar(
                "Ada Lovelace",
                Color(0xFF7A5A8C),
                contentDescription = "Your account",
            )
        },
    )
}

@Composable
internal fun CategoryMarkSample() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryMarkSize.entries.forEach { CategoryMark("Vegetables", Vegetables, size = it) }
        CategoryMark("Flowers", Color(0xFFB03A6A))
        CategoryMark("Help", Color(0xFFF2C94C))
        ForumLogo("Garden club")
        Avatar("Ada Lovelace", Color(0xFF7A5A8C))
    }
}

@Composable
internal fun DotSample() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DotKind.entries.forEach { Dot(it) }
        Dot(
            DotKind.Mention,
            size = 11.dp,
            ring = androidx.compose.material3.MaterialTheme.colorScheme.background,
        )
    }
}

@Composable
internal fun BubbleSample() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Bubble(
            BubbleKind.Mention,
            title = "Mina mentioned you in Seed swap list",
            text = "@ada do you still have Sungold?",
            onClick = {},
            onDismiss = {},
        )
        Bubble(
            BubbleKind.Reaction,
            title = "Rob reacted to your message",
            text = ":agree: Tomatoes need staking early",
            onClick = {},
            onDismiss = {},
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBubble("sent, appends unsent")
            StatusBubble("slow mode", tone = StatusTone.SlowMode)
        }
    }
}

@Composable
internal fun TopicRowSample(expanded: Boolean) {
    TopicRow(
        title = "Tomato plans for spring",
        starter = "Leo Moreau",
        lastActivity = "9:43",
        messagesCount = 24,
        onClick = {},
        tags = listOf("Seeds"),
        dot = DotKind.Reply,
        expansion =
            if (expanded) {
                TopicExpansion(
                    excerpt =
                        "Planning this year's beds. I'll start Cherokee Purple and Sungold " +
                            "indoors in March.",
                    latest =
                        listOf(
                            LatestMessage("Mina", "Sungold every year. Pinch the suckers early."),
                            LatestMessage(
                                "Rob",
                                "Same here, and I leave two leaders on the Cherokees.",
                            ),
                            LatestMessage("Ada", "Which twine do you use for staking?"),
                        ),
                    writers =
                        listOf(
                            "Leo Moreau",
                            "Mina Kader",
                            "Rob Bauer",
                            "Ada Lovelace",
                            "Kim Ng",
                            "Sam Ito",
                        ).mapIndexed {
                            i,
                            name,
                            ->
                            Writer(name, Color(0xFF2C5A28 + i * 0x101820))
                        },
                )
            } else {
                null
            },
    )
}

@Composable
internal fun MessageHeaderSample() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MessageHeader(
            "Mina Kader",
            "9:41",
            role = "Moderator",
            roleColor = Moderator,
            avatar = { Avatar("Mina Kader", Moderator) },
        )
        MessageHeader(
            "Ada Lovelace",
            "9:42",
            userTag = "ada",
            role = "Member",
            avatar = { Avatar("Ada Lovelace", Color(0xFF7A5A8C)) },
        )
    }
}

@PreviewLightDark
@Composable
private fun TopBarPreview() = IdejoTheme(SampleThemes.Garden) { TopBarSample() }

@PreviewLightDark
@Composable
private fun MarksPreview() = IdejoTheme(SampleThemes.Garden) { CategoryMarkSample() }

@PreviewLightDark
@Composable
private fun DotsPreview() = IdejoTheme { DotSample() }

@PreviewLightDark
@Composable
private fun BubblesPreview() = IdejoTheme(SampleThemes.Tennis) { BubbleSample() }

@PreviewLightDark
@Composable
private fun TopicRowPreview() =
    IdejoTheme(SampleThemes.Garden) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TopicRowSample(expanded = false)
            TopicRowSample(expanded = true)
        }
    }

@PreviewLightDark
@Composable
private fun MessageHeaderPreview() = IdejoTheme(SampleThemes.Tennis) { MessageHeaderSample() }
