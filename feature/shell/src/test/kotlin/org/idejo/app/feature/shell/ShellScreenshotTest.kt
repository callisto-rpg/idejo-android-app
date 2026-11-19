// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.idejo.app.core.data.ConversationsState
import org.idejo.app.core.data.LiveBubble
import org.idejo.app.core.data.NotificationKind
import org.idejo.app.core.data.SidebarCategory
import org.idejo.app.core.data.SidebarSection
import org.idejo.app.core.data.SidebarState
import org.idejo.app.core.data.ThemeMode
import org.idejo.app.core.data.TutorialBubbles
import org.idejo.app.core.data.WarningsState
import org.idejo.app.core.designsystem.component.Avatar
import org.idejo.app.core.designsystem.component.Crumb
import org.idejo.app.core.designsystem.component.TopBar
import org.idejo.app.core.designsystem.theme.ForumTheme
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Category
import org.idejo.app.core.model.CategoryGroup
import org.idejo.app.core.model.Conversation
import org.idejo.app.core.model.MentionState
import org.idejo.app.core.model.Role
import org.idejo.app.core.model.Topic
import org.idejo.app.core.model.Warning
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** The forum's frame in the Garden sample's colours, light and dark (SH-10 to SH-38). */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class ShellScreenshotTest {
    @Test
    fun `SH-31 SH-32 SH-34 the sidebar with dots and the open topic`() =
        snapshots("Sidebar") {
            Sidebar(
                SIDEBAR,
                emptySet(),
                false,
                signedIn = true,
                openTopic = OPEN,
                actions = NoActions,
            )
        }

    @Test
    fun `SH-33 SH-35 PC-01 folded, the links rolled, Private Conversations at the foot`() =
        snapshots("SidebarFolded") {
            Sidebar(
                SIDEBAR,
                setOf("c_veg"),
                true,
                signedIn = true,
                openTopic = null,
                actions = NoActions,
                conversations =
                    ConversationsState(
                        available = true,
                        conversations =
                            listOf(
                                Conversation("p_1", unreadCount = 2),
                                Conversation("p_2"),
                                Conversation("p_3", unreadCount = 1),
                            ),
                        loading = false,
                    ),
            )
        }

    @Test
    fun `SH-10 SH-11 SH-14 the logo menu in single-forum mode`() =
        snapshots("LogoMenu") {
            LogoMenu("Garden club", "garden.example.org", {}, {}, {})
        }

    @Test
    fun `SI-07 the logo's dot and Your forums in idejo ID mode`() {
        snapshots("TopBarNewForum") {
            TopBar(listOf(Crumb.Forum("Garden club", {}, dot = true), Crumb.Page("Home")))
        }
        snapshots("LogoMenuIdejoId") {
            LogoMenu(
                "Garden club",
                "garden.example.org",
                {},
                {},
                {},
                onForums = {},
                newForums = true,
            )
        }
    }

    @Test
    fun `SH-20 SH-21 SH-22 SH-23 SH-24 the user menu of an administrator`() =
        snapshots("UserMenu") {
            UserMenu(
                forumName = "Garden club",
                member =
                    MemberCard(
                        "Ada Lovelace",
                        "ada",
                        listOf(
                            Role("Gardener", colour = "#3f6b3a"),
                            Role("Admin", colour = "#b5523b"),
                        ),
                    ),
                administrator = true,
                canSignUp = true,
                themeMode = ThemeMode.System,
                tutorialBubbles = TutorialBubbles.Simple,
                actions = NoActions,
                onClose = {},
                avatar = {
                    Avatar(
                        "Ada Lovelace",
                        MaterialTheme.colorScheme.tertiary,
                        size = 72.dp,
                    )
                },
            )
        }

    @Test
    fun `SH-25 the user menu of a visitor`() =
        snapshots("UserMenuVisitor") {
            UserMenu(
                forumName = "Garden club",
                member = null,
                administrator = false,
                canSignUp = true,
                themeMode = ThemeMode.Dark,
                tutorialBubbles = TutorialBubbles.All,
                actions = NoActions,
                onClose = {},
            )
        }

    @Test
    fun `TV-11 the warning card`() =
        snapshots("Warning") {
            WarningCard(
                WarningsState(
                    listOf(
                        Warning(
                            id = "w_1",
                            reason = "Please keep it civil: no name-calling.",
                            messageId = "m_1",
                            createdAt = Instant.parse("2026-11-14T16:05:00Z"),
                        ),
                    ),
                ),
                WarningActions({}, {}, {}),
            )
        }

    @Test
    fun `NT-07 the notifications bar`() =
        snapshots("PushBar") {
            PushBarContent(on = false, bubble = true, onEnable = {}, onDismiss = {})
        }

    @Test
    fun `NT-01 the live bubbles`() =
        snapshots("Bubbles") {
            LiveBubbleStack(
                listOf(
                    LiveBubble(
                        3,
                        NotificationKind.Mention,
                        "Mina",
                        "Tomatoes",
                        "@ada which sauce?",
                        "t_1",
                        "m_3",
                        null,
                    ),
                    LiveBubble(
                        2,
                        NotificationKind.Reply,
                        "Bea",
                        "Basil all summer",
                        "Pinch the tops.",
                        "t_2",
                        "m_2",
                        null,
                    ),
                    LiveBubble(
                        1,
                        NotificationKind.RoleMention,
                        "Cy",
                        "Seed swap",
                        null,
                        "t_3",
                        "m_1",
                        null,
                    ),
                ),
                onOpen = {},
                onDismiss = {},
                modifier = Modifier.padding(12.dp),
            )
        }

    private fun snapshots(
        name: String,
        content: @Composable () -> Unit,
    ) {
        for (dark in listOf(false, true)) {
            val mode = if (dark) "dark" else "light"
            captureRoboImage("src/test/screenshots/${name}_$mode.png", OPTIONS) {
                IdejoTheme(GARDEN, dark) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.width(390.dp).height(844.dp)) { content() }
                    }
                }
            }
        }
    }

    private object NoActions : SidebarActions, UserMenuActions {
        override fun open(place: Place) = Unit

        override fun fold(categoryId: String) = Unit

        override fun rollLinks(rolled: Boolean) = Unit

        override fun follow(
            topic: Topic,
            followed: Boolean,
        ) = Unit

        override fun unfollowAndMute(topic: Topic) = Unit

        override fun markUnread(topic: Topic) = Unit

        override fun copyLink(topic: Topic) = Unit

        override fun share(topic: Topic) = Unit

        override fun retry() = Unit

        override fun themeMode(mode: ThemeMode) = Unit

        override fun tutorialBubbles(choice: TutorialBubbles) = Unit

        override fun profileAndAccount() = Unit

        override fun appSettings() = Unit

        override fun admin() = Unit

        override fun signIn() = Unit

        override fun signUp() = Unit

        override fun signOut() = Unit

        override fun remove() = Unit
    }

    private companion object {
        val GARDEN = ForumTheme.fromHex("#3f6b3a", "#8a6d3b", "#b5523b", "#f6f3ea", "#1d211b")
        val OPTIONS =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            )

        fun topic(
            id: String,
            title: String,
            category: String,
            state: MentionState = MentionState.None,
        ) = Topic(
            id = id,
            title = title,
            categoryId = category,
            followed = true,
            mentionState = state,
            unreadCount = if (state == MentionState.None) 0 else 1,
        )

        val OPEN =
            Topic(id = "t_open", title = "Which compost for clay soil?", categoryId = "c_soil")

        val SIDEBAR =
            SidebarState(
                loading = false,
                sections =
                    listOf(
                        SidebarSection(
                            CategoryGroup("g_grow", "Growing", 1),
                            listOf(
                                SidebarCategory(
                                    Category(
                                        "c_veg",
                                        "Vegetables",
                                        "vegetables",
                                        colour = "#8a6a1f",
                                    ),
                                    listOf(
                                        topic(
                                            "t_1",
                                            "Tomato plans for spring",
                                            "c_veg",
                                            MentionState.Mention,
                                        ),
                                        topic(
                                            "t_2",
                                            "Peppers indoors",
                                            "c_veg",
                                            MentionState.Reply,
                                        ),
                                        topic("t_3", "Seed swap list", "c_veg"),
                                        topic("t_4", "Slugs, again", "c_veg", MentionState.Role),
                                        topic(
                                            "t_5",
                                            "Garlic in October",
                                            "c_veg",
                                            MentionState.New,
                                        ),
                                        topic("t_6", "Beans on poles", "c_veg"),
                                    ),
                                ),
                                SidebarCategory(
                                    Category(
                                        "c_soil",
                                        "Soil and compost",
                                        "soil",
                                        colour = "#5b4636",
                                    ),
                                    emptyList(),
                                ),
                            ),
                        ),
                        SidebarSection(
                            null,
                            listOf(
                                SidebarCategory(
                                    Category("c_chat", "Chat", "chat", colour = "#3f6b3a"),
                                    listOf(topic("t_7", "Hello from Lyon", "c_chat")),
                                ),
                            ),
                        ),
                    ),
            )
    }
}
