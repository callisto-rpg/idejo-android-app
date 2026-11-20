// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** ST-20: **Open links in the app**, as Android has it. */
@RunWith(RobolectricTestRunner::class)
class LinkHandlingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `ST-20 verified or chosen hosts count as open, turned off is off`() {
        val states = mapOf("my.idejo.org" to 1, "*.idejo.org" to 0, "forum.example.org" to 2)
        assertEquals(LinkHandling.On(2, 3), linkHandling(allowed = true, states))
        assertEquals(LinkHandling.Off, linkHandling(allowed = false, states))
    }

    @Test
    fun `ST-20 the section says so and leads to Android's settings`() {
        var asked = false
        rule.setContent { LinkHandlingRow(LinkHandling.On(2, 3), onChange = { asked = true }) }
        rule.onNodeWithText("Links to 2 of 3 forum addresses open in the app.").assertExists()
        rule.onNodeWithText("Change in Android settings").performClick()
        assertTrue(asked)
    }
}
