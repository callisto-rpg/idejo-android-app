// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import org.idejo.app.core.data.ForumRecord
import org.idejo.app.core.data.GroupForum
import org.idejo.app.core.data.InboxTopicsState
import org.idejo.app.core.model.Discovery
import org.idejo.app.core.model.MentionState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The logo menu in idejo ID mode (SH-12 to SH-15). */
@RunWith(RobolectricTestRunner::class)
class LogoMenuTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val done = mutableListOf<String>()
    private val garden = "https://garden.example.org/"
    private val bees = "https://bees.example.org/"

    private fun menu(group: GroupMenu) =
        rule.setContent {
            LogoMenu(
                "Garden club",
                "garden.example.org",
                {},
                {},
                {},
                group = group,
                actions = RecordedActions(done),
            )
        }

    private val forums =
        listOf(
            YourForum(garden, "Garden club", current = true),
            YourForum(bees, "Bee keepers", level = MentionState.Mention),
        )

    @Test
    fun `SH-12 every forum held with its dot, tapping one switches to it`() {
        menu(GroupMenu(forums))
        rule.onNodeWithText("Open now").assertExists()
        rule.onNodeWithContentDescription("You were mentioned").assertExists()
        rule.onNodeWithText("Bee keepers").performScrollTo().performClick()
        assertEquals(listOf("enter $bees"), done)
    }

    @Test
    fun `SH-12 the list is the forums held, then the group's not held, to connect`() {
        val held =
            listOf(
                ForumRecord(garden, Discovery("Garden club", garden), sealedToken = "sealed"),
                ForumRecord(bees, Discovery("Bee keepers", bees)),
            )
        val joined =
            listOf(
                GroupForum("Bee keepers", bees),
                GroupForum("Weavers", "https://weave.example.org/", new = true),
            )
        val read = InboxTopicsState(levels = mapOf(garden to MentionState.Reply))

        val list = yourForums(held, joined, read, current = garden)

        assertEquals(listOf("Garden club", "Bee keepers", "Weavers"), list.map { it.name })
        assertEquals(listOf(true, false, false), list.map { it.current })
        assertEquals(listOf(false, true, true), list.map { it.connect })
        assertEquals(listOf(true, true, false), list.map { it.held })
        assertEquals(MentionState.Reply, list.first().level)
        assertEquals(listOf(false, false, true), list.map { it.new })
    }

    @Test
    fun `SH-13 All my topics and Private Conversations with its count`() {
        menu(GroupMenu(forums, chatsWithNews = 3))
        rule.onNodeWithText("3").assertExists()
        rule.onNodeWithText("All my topics").performScrollTo().performClick()
        rule.onNodeWithText("Private Conversations").performScrollTo().performClick()
        assertEquals(listOf("topics", "chats"), done)
    }

    @Test
    fun `SH-14 Add a forum by address or from the directory`() {
        menu(GroupMenu(forums, directory = true))
        rule.onNodeWithText("By address").performScrollTo().performClick()
        rule.onNodeWithText("Find more forums").performScrollTo().performClick()
        assertEquals(listOf("address", "find"), done)
    }

    @Test
    fun `SH-14 a group without a directory has no Find more forums`() {
        menu(GroupMenu(forums))
        rule.onNodeWithText("Find more forums").assertDoesNotExist()
    }

    @Test
    fun `SH-15 long-press a forum, Remove from app after a confirmation, Open in browser`() {
        menu(GroupMenu(forums))
        rule.onNodeWithText("Bee keepers").performScrollTo().performTouchInput { longClick() }
        rule.onNodeWithText("Open in browser").performClick()
        rule.onNodeWithText("Bee keepers").performScrollTo().performTouchInput { longClick() }
        rule.onNodeWithText("Remove from app").performClick()
        rule.onNodeWithText("Remove Bee keepers from the app?").assertExists()
        rule.onNodeWithText("Remove").performClick()
        assertEquals(listOf("browser $bees", "remove $bees"), done)
    }

    @Test
    fun `SH-30 the sidebar opens from the forum menu, not the top bar`() {
        rule.setContent {
            LogoMenu("Garden club", "garden.example.org", {}, {}, {}, onSidebar = {
                done +=
                    "sidebar"
            })
        }
        rule.onNodeWithText("Followed topics").performClick()
        assertEquals(listOf("sidebar"), done)
    }

    @Test
    fun `SH-11 the forum's name opens home, Support is greyed`() {
        rule.setContent {
            LogoMenu(
                "Garden club",
                "garden.example.org",
                { done += "home" },
                {},
                {},
            )
        }
        rule.onNodeWithText("Garden club").performClick()
        assertEquals(listOf("home"), done)
        rule.onNodeWithText("Support").assertIsNotEnabled()
        rule.onNodeWithText("Home").assertDoesNotExist()
    }

    @Test
    fun `SH-11 when the labels do not fit, the leftmost lose theirs first`() {
        val extra = listOf(100, 50, 60)
        assertEquals(3, labelled(bare = 48, extra, room = 400))
        assertEquals(2, labelled(bare = 48, extra, room = 260))
        assertEquals(1, labelled(bare = 48, extra, room = 210))
        assertEquals(0, labelled(bare = 48, extra, room = 150))
    }

    @Test
    fun `SH-30 docked, the forum menu has no Sidebar`() {
        rule.setContent { LogoMenu("Garden club", "garden.example.org", {}, {}, {}) }
        rule.onNodeWithText("Followed topics").assertDoesNotExist()
    }

    @Test
    fun `PC-01 without idejo ID, Private Conversations is greyed, Connect signs in with it`() {
        rule.setContent {
            LogoMenu(
                "Garden club",
                "garden.example.org",
                {},
                {},
                {},
                actions = RecordedActions(done),
                chatService = true,
            )
        }
        rule.onNodeWithText("Private Conversations").assertIsNotEnabled()
        rule
            .onNodeWithContentDescription(
                "Connect idejo ID for private conversations",
            ).performClick()
        assertEquals(listOf("idejo ID"), done)
    }

    @Test
    fun `PC-01 a forum without a chat service has no Private Conversations`() {
        rule.setContent {
            LogoMenu(
                "Garden club",
                "garden.example.org",
                {},
                {},
                {},
                actions = RecordedActions(done),
            )
        }
        rule.onNodeWithText("Private Conversations").assertDoesNotExist()
    }
}
