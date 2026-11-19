// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.idejo.app.core.data.InboxTopicChoices
import org.idejo.app.core.data.TopicView
import org.idejo.app.feature.inbox.InboxSamples.TENNIS
import org.idejo.app.feature.inbox.InboxSamples.doubles
import org.idejo.app.feature.inbox.InboxSamples.state
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The inbox: its frame and tabs (IB-01), **Topics** (IB-03). */
@RunWith(RobolectricTestRunner::class)
class InboxScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `IB-01 the breadcrumb follows the tab, three tabs`() {
        rule.setContent {
            InboxScreen(
                state,
                forums = { Text("forums page") },
                topics = { Text("topics page") },
                chats = { Text("chats page") },
            )
        }
        // The breadcrumb and the tab.
        rule.onAllNodesWithText("Forums").assertCountEquals(2)
        rule.onNodeWithText("forums page").assertExists()
        rule.onNodeWithText("Topics").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("topics page").assertExists()
        rule.onAllNodesWithText("Topics").assertCountEquals(2)
        rule.onNodeWithText("Chats").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("chats page").assertExists()
        rule.onAllNodesWithText("Chats").assertCountEquals(2)
    }

    @Test
    fun `MD-05 the tab left last opens again, each tab shown is kept`() {
        val shown = mutableListOf<InboxTab>()
        rule.setContent {
            InboxScreen(
                state,
                forums = { Text("forums page") },
                topics = { Text("topics page") },
                chats = { Text("chats page") },
                restoredTab = InboxTab.Chats,
                onTab = { shown += it },
            )
        }
        rule.waitForIdle()
        rule.onNodeWithText("chats page").assertExists()
        rule.onNodeWithText("Topics").performClick()
        rule.waitForIdle()
        assertEquals(InboxTab.Topics, shown.last())
        assertTrue(InboxTab.Chats in shown)
    }

    @Test
    fun `IB-06 a chat under the breadcrumb Chats and its name, both leading back`() {
        var back = 0
        rule.setContent {
            ChatFrame("idejo", null, "Bo Garden", onBack = { back++ }) { Text("the topic page") }
        }
        rule.onNodeWithText("the topic page").assertExists()
        rule.onNodeWithText("Bo Garden").assertExists()
        rule.onNodeWithText("Chats").performClick()
        assertEquals(1, back)
    }

    @Test
    fun `IB-03 IB-04 each topic with its forum and counts, a forum unreachable named`() {
        val events = mutableListOf<InboxEvent>()
        rule.setContent { TopicsTab(state, { events += it }) }
        rule.onNodeWithText("Couldn't reach Chess club").assertExists()
        rule.onNodeWithText("3 new messages").assertExists()
        rule.onNodeWithText("1 mention").assertExists()
        rule.onNodeWithText("2 reactions").assertExists()
        rule.onNodeWithText("Saturday doubles").performClick()
        assertEquals(listOf<InboxEvent>(InboxEvent.Open(doubles)), events)
    }

    @Test
    fun `IB-03 the toolbar's choices`() {
        val events = mutableListOf<InboxEvent>()
        rule.setContent { TopicsTab(state, { events += it }) }
        rule.onNodeWithText("Unread only").performClick()
        rule.onNodeWithText("Compact ▾").performClick()
        rule.onNodeWithText("Expanded").performClick()
        rule.onNodeWithText("Filter").performClick()
        rule.onNodeWithText("Tennis").performClick()
        assertEquals(
            listOf(
                InboxEvent.UnreadOnly(true),
                InboxEvent.View(TopicView.Expanded),
                InboxEvent.Hidden(setOf(TENNIS)),
            ),
            events,
        )
    }

    @Test
    fun `IB-03 Unread only and Filter, as remembered`() {
        val unread = state.copy(choices = InboxTopicChoices(unreadOnly = true))
        assertEquals(
            listOf("Tomato plans", "Saturday doubles"),
            unread.shown.map { it.topic.title },
        )
        val gardenOnly = state.copy(choices = InboxTopicChoices(hidden = setOf(TENNIS)))
        assertEquals(listOf("Tomato plans", "Seed swap"), gardenOnly.shown.map { it.topic.title })
    }
}
