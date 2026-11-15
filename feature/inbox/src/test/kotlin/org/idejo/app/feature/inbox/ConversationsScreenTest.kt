// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.idejo.app.core.data.ConversationsState
import org.idejo.app.core.model.Conversation
import org.idejo.app.core.model.ConversationLast
import org.idejo.app.core.model.Person
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** **Private Conversations** as cards (PC-01). */
@RunWith(RobolectricTestRunner::class)
class ConversationsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `PC-01 each conversation as a card, its latest message and how many are new`() {
        val opened = mutableListOf<String>()
        val mina = Person("u_mina", "Mina Kader")
        rule.setContent {
            ConversationsScreen(
                ConversationsState(
                    available = true,
                    conversations =
                        listOf(
                            Conversation(
                                "p_1",
                                with = listOf(mina),
                                unreadCount = 2,
                                lastMessage =
                                    ConversationLast(
                                        "m_9",
                                        "Mina Kader",
                                        "Seeds for you.",
                                    ),
                            ),
                        ),
                    loading = false,
                ),
                origin = "https://garden.example",
                onConversation = { opened += it },
                onRefresh = {},
            )
        }
        rule.onNodeWithText("Mina Kader: Seeds for you.").assertExists()
        rule.onNodeWithText("2 new").assertExists()
        rule.onNodeWithText("Mina Kader").performClick()
        assertEquals(listOf("p_1"), opened)
    }

    @Test
    fun `PC-01 with none yet, how to start one`() {
        rule.setContent {
            ConversationsScreen(ConversationsState(available = true, loading = false), "", {}, {})
        }
        rule.onNodeWithText("Private Message", substring = true).assertExists()
    }
}
