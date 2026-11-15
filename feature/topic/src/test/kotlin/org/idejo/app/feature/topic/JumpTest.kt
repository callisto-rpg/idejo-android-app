// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.MessageState
import org.idejo.app.core.model.Person
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The jump button's entries and where they lead (SH-04). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class JumpTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val went = mutableListOf<String>()
    private val actions =
        JumpActions(
            onFirst = { went += "first" },
            onUnread = { went += "unread" },
            onLast = { went += "last" },
            onMessage = { went += it },
        )
    private val pin =
        Message(
            "m_pin",
            "t_1",
            Person("u_mina", "Mina Kader"),
            MessageState.Closed,
            bodyMd = "Rules of the swap",
        )

    private fun open(jumps: Jumps) {
        rule.setContent { JumpButton(jumps, actions) }
        rule.onNodeWithContentDescription("Jump to a message").performClick()
    }

    @Test
    fun `SH-04 every entry, as the web's navigator`() {
        open(Jumps(unread = true, mine = "m_mine", pins = listOf(pin)))
        rule.onNodeWithText("First message").assertExists()
        rule.onNodeWithText("First unread").assertExists()
        rule.onNodeWithText("Last message").assertExists()
        rule.onNodeWithText("Pinned").assertExists()
        rule.onNodeWithText("My last message").performClick()
        assertEquals(listOf("m_mine"), went)
    }

    @Test
    fun `SH-04 a pinned message opens where it is`() {
        open(Jumps(unread = false, mine = null, pins = listOf(pin)))
        rule.onNodeWithText("Mina Kader · Rules of the swap").performClick()
        assertEquals(listOf("m_pin"), went)
    }

    @Test
    fun `SH-04 without unread messages, own messages or pins, only the first and the last`() {
        open(Jumps(unread = false, mine = null, pins = emptyList()))
        rule.onNodeWithText("First unread").assertDoesNotExist()
        rule.onNodeWithText("My last message").assertDoesNotExist()
        rule.onNodeWithText("Pinned").assertDoesNotExist()
        rule.onNodeWithText("Last message").performClick()
        assertEquals(listOf("last"), went)
    }
}
