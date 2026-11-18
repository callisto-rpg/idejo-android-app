// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The notifications bar (NT-07): Android 13+'s permission, asked as the web's banner. */
@RunWith(RobolectricTestRunner::class)
class PushBarTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var dismissed = 0
    private val offer = "Push notifications not enabled. You will not be notified of new messages."

    private fun show(
        place: Place = Place.Topic("t_1"),
        offered: Boolean = true,
        bubble: Boolean = true,
    ) = rule.setContent {
        IdejoTheme { PushBar(offered, bubble, place) { dismissed++ } }
    }

    @Test
    fun `NT-07 on a topic page, once offered, in the web's words`() {
        show()
        rule.onNodeWithText(offer).assertExists()
        rule.onNodeWithText("Enable").assertExists()
        rule.onNodeWithTag(PUSH_BAR_BUBBLE).assertExists()
    }

    @Test
    fun `NT-07 Dismiss keeps it away`() {
        show()
        rule.onNodeWithText("Dismiss").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun `NT-07 not before the member follows a topic`() {
        show(offered = false)
        rule.onNodeWithText(offer).assertDoesNotExist()
    }

    @Test
    fun `NT-07 not on the home page`() {
        show(place = Place.Home)
        rule.onNodeWithText(offer).assertDoesNotExist()
    }

    @Test
    fun `NT-07 not once Android may notify`() {
        shadowOf(rule.activity.application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        show()
        rule.onNodeWithText(offer).assertDoesNotExist()
    }

    @Test
    fun `SH-21 the tutorial bubble follows Tutorial bubbles`() {
        show(bubble = false)
        rule.onNodeWithText(offer).assertExists()
        rule
            .onNodeWithText(
                "This can always be turned off and on in user settings",
            ).assertDoesNotExist()
    }
}
