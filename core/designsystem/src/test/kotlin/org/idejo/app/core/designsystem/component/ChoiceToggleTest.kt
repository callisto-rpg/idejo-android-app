// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The app's one toggle selector (steward request 2026-11-23). */
@RunWith(RobolectricTestRunner::class)
class ChoiceToggleTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var chosen by mutableStateOf("One")

    private fun show() {
        rule.setContent {
            ChoiceToggle(
                listOf("One", "Two", "Three"),
                chosen,
                { chosen = it },
                Modifier.width(300.dp).testTag("toggle"),
                fill = true,
            ) { it }
        }
    }

    @Test
    fun `a tap picks the option and it reads as selected`() {
        show()
        rule.onNodeWithText("Two").performClick()
        rule.waitForIdle()
        assertEquals("Two", chosen)
        rule.onNodeWithText("Two").assertIsSelected()
        rule.onNodeWithText("One").assertIsNotSelected()
    }

    @Test
    fun `the selector dragged across picks the option it is let go nearest`() {
        show()
        rule.onNodeWithTag("toggle").performTouchInput {
            down(centerLeft.copy(x = width / 6f))
            moveBy(centerLeft.copy(x = width * 2 / 3f, y = 0f), delayMillis = 100)
            up()
        }
        rule.waitForIdle()
        assertEquals("Three", chosen)
    }

    @Test
    fun `a short drag springs back to the option chosen`() {
        show()
        rule.onNodeWithTag("toggle").performTouchInput {
            down(centerLeft.copy(x = width / 6f))
            moveBy(centerLeft.copy(x = width / 12f, y = 0f), delayMillis = 100)
            up()
        }
        rule.waitForIdle()
        assertEquals("One", chosen)
    }
}
