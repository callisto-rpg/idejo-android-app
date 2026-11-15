// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.idejo.app.feature.settings.writing.WritingEvent
import org.idejo.app.feature.settings.writing.WritingScreen
import org.idejo.app.feature.settings.writing.WritingUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Profile and account's overview and sections (ST-02). */
@RunWith(RobolectricTestRunner::class)
class ProfileAndAccountTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `ST-02 the overview lists the sections under the web's tabs`() {
        rule.setContent { ProfileAndAccountScreen(ProfileAndAccountUiState(SETTINGS_SECTIONS), {}) }
        rule.onNodeWithText("Interface").assertExists()
        rule.onNodeWithText("Writing").assertExists()
        rule.onNodeWithText("Messages and notifications").assertExists()
        rule.onNodeWithText("Private messages").assertExists()
    }

    @Test
    fun `ST-02 a switch shows the choice and sends the change`() {
        val events = mutableListOf<WritingEvent>()
        rule.setContent {
            WritingScreen(
                WritingUiState(loading = false, autoPunctuation = true, graphicalComposer = false),
                onEvent = { events += it },
            )
        }
        rule.onNodeWithText("Use the graphical composer").assertIsOff()
        rule.onNodeWithText("Automatic punctuation").assertIsOn().performClick()
        assertEquals(listOf<WritingEvent>(WritingEvent.AutoPunctuation(false)), events)
    }

    @Test
    fun `ST-02 a choice the server does not send has no row`() {
        rule.setContent {
            WritingScreen(WritingUiState(loading = false, autoPunctuation = true), onEvent = {})
        }
        rule.onNodeWithText("Use the graphical composer").assertDoesNotExist()
    }
}
