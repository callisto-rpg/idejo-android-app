// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.idejo.app.core.data.LiveBubble
import org.idejo.app.core.data.NotificationKind
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The live bubbles at the top (NT-01). */
@RunWith(RobolectricTestRunner::class)
class LiveBubbleStackTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun bubble(
        key: Long,
        kind: NotificationKind,
    ) = LiveBubble(key, kind, "Mina", "Tomatoes", "Sungold every year", "t_1", "m_1", null)

    private val opened = mutableListOf<Long>()
    private val dismissed = mutableListOf<Long>()

    private fun show(vararg shown: LiveBubble) {
        rule.setContent {
            var bubbles by remember { mutableStateOf(shown.toList()) }
            IdejoTheme {
                LiveBubbleStack(
                    bubbles,
                    onOpen = { opened += it.key },
                    onDismiss = { gone ->
                        dismissed += gone.key
                        bubbles = bubbles - gone
                    },
                )
            }
        }
    }

    @Test
    fun `NT-01 the web's words, and a tap opens the message`() {
        show(bubble(1, NotificationKind.Mention), bubble(2, NotificationKind.Reply))
        rule.onNodeWithText("Mina mentioned you in “Tomatoes”").assertExists()
        rule.onNodeWithText("Mina replied to you in “Tomatoes”").performClick()
        assertEquals(listOf(2L), opened)
    }

    @Test
    fun `NT-01 a bubble goes after ten seconds`() {
        rule.mainClock.autoAdvance = false
        show(bubble(1, NotificationKind.RoleMention))
        rule.mainClock.advanceTimeBy(9_000)
        rule.onNodeWithText("Mina mentioned one of your roles in “Tomatoes”").assertExists()
        rule.mainClock.advanceTimeBy(1_500)
        rule.waitForIdle()
        assertEquals(listOf(1L), dismissed)
    }

    @Test
    fun `AX-04 the x dismisses a bubble, no swipe needed`() {
        show(bubble(1, NotificationKind.Followed))
        rule.onNodeWithContentDescription("Dismiss").performClick()
        rule.onNodeWithText("Mina posted in “Tomatoes”").assertDoesNotExist()
        assertEquals(listOf(1L), dismissed)
    }
}
