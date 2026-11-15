// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** "Members can send me private messages" (PC-03). */
@RunWith(RobolectricTestRunner::class)
class PrivateMessagesTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `PC-03 the switch shows the member's choice and changes it`() {
        val changed = mutableListOf<Boolean>()
        rule.setContent {
            AppSettingsScreen(
                "0.0.1",
                emptyList(),
                privateMessages = PrivateMessagesUi(accepts = true),
                onPrivateMessages = { changed += it },
            )
        }
        rule.onNodeWithText("Members can send me private messages").assertIsOn().performClick()
        assertEquals(listOf(false), changed)
    }

    @Test
    fun `PC-03 no switch for a visitor`() {
        rule.setContent { AppSettingsScreen("0.0.1", emptyList()) }
        rule.onNodeWithText("Members can send me private messages").assertDoesNotExist()
    }
}
