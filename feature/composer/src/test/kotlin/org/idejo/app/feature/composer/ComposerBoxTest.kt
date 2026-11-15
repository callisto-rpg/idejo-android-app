// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import org.idejo.app.core.model.ApiError
import org.idejo.app.core.model.Me
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class ComposerBoxTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val did = mutableListOf<String>()
    private val events =
        ComposerEvents(
            onTyped = { did += "typed:$it" },
            onEnter = { did += "enter" },
            onComplete = { did += "complete" },
            onErrorSeen = { did += "seen" },
        )
    private val ada = Me("u_1", "Ada Lovelace")

    /** The box over [start], its text following what is typed, as the machine's does. */
    private fun show(start: ComposerState = ComposerState()) =
        rule.setContent {
            var state by remember { mutableStateOf(start) }
            val following =
                events.copy(onTyped = {
                    events.onTyped(it)
                    state = state.copy(text = it)
                })
            ComposerBox(state, ada, "https://garden.idejo.test", following)
        }

    private fun box() = rule.onNodeWithContentDescription("Message")

    @Test
    fun `CO-03 hardware Enter sends, Shift+Enter breaks the line, Ctrl+Enter completes`() {
        show()
        box().performTextInput("Hi")
        box().performKeyInput { pressKey(Key.Enter) }
        box().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Enter) } }
        box().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Enter) } }
        box().performKeyInput { withKeyDown(Key.MetaLeft) { pressKey(Key.Enter) } }
        assertEquals(
            listOf("typed:Hi", "enter", "typed:Hi\n", "complete", "complete"),
            did,
        )
    }

    @Test
    fun `CO-03 the soft keyboard's return is an Enter`() {
        show(ComposerState(text = "Hi"))
        box().performTextInput("\n")
        assertEquals(listOf("enter"), did)
    }

    @Test
    fun `CO-05 the status bubble says what is sent`() {
        show(ComposerState(text = "Hi. there", sent = "Hi.", openMessageId = "m_1"))
        rule.onNodeWithText("sent, appends unsent").assertExists()
    }

    @Test
    fun `CO-08 the server's message shows under the box, with Retry-After`() {
        val error = ApiError(ApiError.RATE_LIMITED, "You are sending too fast.")
        show(
            ComposerState(text = "Hi", error = ComposerError.Refused(error, Duration.ofSeconds(5))),
        )
        rule.onNodeWithText("You are sending too fast. Try again in 5 seconds.").assertExists()
        rule.onNodeWithText("Dismiss").performClick()
        assertEquals(listOf("seen"), did)
    }
}
