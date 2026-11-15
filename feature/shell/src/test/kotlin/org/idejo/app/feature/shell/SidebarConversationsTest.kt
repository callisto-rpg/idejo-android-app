// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.idejo.app.core.data.ConversationsState
import org.idejo.app.core.data.SidebarState
import org.idejo.app.core.model.Conversation
import org.idejo.app.core.model.Topic
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** **Private Conversations** at the sidebar's foot (PC-01, SH-36). */
@RunWith(RobolectricTestRunner::class)
class SidebarConversationsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<Place>()

    private val actions =
        object : SidebarActions {
            override fun open(place: Place) {
                opened += place
            }

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
        }

    private fun show(conversations: ConversationsState) =
        rule.setContent {
            Sidebar(
                SidebarState(loading = false),
                emptySet(),
                false,
                signedIn = true,
                openTopic = null,
                actions = actions,
                conversations = conversations,
            )
        }

    @Test
    fun `PC-01 the count of conversations with news, and a tap opens them`() {
        show(
            ConversationsState(
                available = true,
                conversations =
                    listOf(
                        Conversation("p_1", unreadCount = 3),
                        Conversation("p_2"),
                        Conversation("p_3", unreadCount = 1),
                    ),
                loading = false,
            ),
        )
        val row = "Private Conversations, 2 with something new"
        rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(row))
        rule.onNodeWithContentDescription(row).performClick()
        assertEquals(listOf<Place>(Place.Conversations), opened)
    }

    @Test
    fun `PC-01 none on a forum without private messages`() {
        show(ConversationsState(available = false, loading = false))
        rule
            .onNodeWithContentDescription(
                "Private Conversations",
                substring = true,
            ).assertDoesNotExist()
    }
}
