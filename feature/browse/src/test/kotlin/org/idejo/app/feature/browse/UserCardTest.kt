// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.idejo.app.core.model.UserCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What a member does from someone's user card (MA-06, PC-03). */
@RunWith(RobolectricTestRunner::class)
class UserCardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val leo = UserCard("u_leo", "Leo Moreau", ignoredByMe = false)

    @Test
    fun `MA-06 Ignore from the user card`() {
        val asked = mutableListOf<Boolean>()
        rule.setContent {
            UserCardScreen(UserCardUi(leo, loading = false), { asked += it }, {}, {})
        }
        rule.onNodeWithText("Ignore").performClick()
        assertEquals(listOf(true), asked)
    }

    @Test
    fun `PC-03 Private Message only for someone who takes them`() {
        var messaged = 0
        var card by mutableStateOf(leo.copy(mayMessage = true))
        rule.setContent {
            UserCardScreen(
                UserCardUi(card, loading = false),
                {},
                {},
                {},
                onMessage = { messaged++ },
            )
        }
        rule.onNodeWithText("Private Message").performClick()
        assertEquals(1, messaged)
        card = leo.copy(mayMessage = false)
        rule.onNodeWithText("Private Message").assertDoesNotExist()
    }

    @Test
    fun `IB-07 PC-02 with a chat service, Private Message by the idejo ID`() {
        val chat = "https://my.example.org/chat"
        var card by mutableStateOf(leo.copy(mayMessage = true, idejoId = null))
        rule.setContent {
            UserCardScreen(
                UserCardUi(card, loading = false, chat = chat),
                {},
                {},
                {},
            )
        }
        // Linked members write at the chat service: no idejo ID, no button, as on the web.
        rule.onNodeWithText("Private Message").assertDoesNotExist()
        card = leo.copy(mayMessage = false, idejoId = "leo-sub")
        rule.onNodeWithText("Private Message").assertExists()
        assertEquals("$chat/new?with=leo-sub", chatLink(chat, "leo-sub"))
    }

    @Test
    fun `PC-02 single-forum mode, a line offers idejo ID mode`() {
        var offered = 0
        var single by mutableStateOf(true)
        rule.setContent {
            UserCardScreen(
                UserCardUi(
                    leo.copy(idejoId = "leo-sub"),
                    loading = false,
                    chat = "https://my.example.org/chat",
                    single = single,
                ),
                {},
                {},
                {},
                onIdejoId = { offered++ },
            )
        }
        rule.onNodeWithText("Sign in with idejo ID").performClick()
        assertEquals(1, offered)
        // In idejo ID mode the chat is the inbox's already.
        single = false
        rule.onNodeWithText("Sign in with idejo ID").assertDoesNotExist()
    }
}
