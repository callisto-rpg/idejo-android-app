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
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import org.idejo.app.core.model.ApiError
import org.idejo.app.core.model.Command
import org.idejo.app.core.model.Me
import org.idejo.app.core.model.Suggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            suggest = { sign, q ->
                listOf(Suggestion("user", "ada", "Ada Lovelace", "u_1")).filter {
                    sign == '@' &&
                        "ada".startsWith(q)
                }
            },
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

    @Test
    fun `CO-10 the paper plane sends, its menu starts a new message`() {
        show(ComposerState(text = "Hi"))
        rule.onNodeWithContentDescription("Send").performClick()
        rule.onNodeWithContentDescription("Send").performTouchInput { longClick() }
        rule.onNodeWithText("Send & start new message").performClick()
        assertEquals(listOf("enter", "complete"), did)
    }

    @Test
    fun `CO-10 while editing, the menu saves`() {
        show(ComposerState(text = "Hi", editing = Editing("m_2", ComposerState())))
        rule.onNodeWithContentDescription("Save").performTouchInput { longClick() }
        rule.onNodeWithText("Save and end the edit").assertExists()
    }

    @Test
    fun `CO-10 the pencil's Bulleted list`() {
        show(ComposerState(text = "seeds"))
        rule.onNodeWithContentDescription("Text controls").performClick()
        rule.onNodeWithText("Bulleted list").performClick()
        assertEquals("typed:- seeds", did.last())
    }

    @Test
    fun `CO-12 suggestions after @, taken with the arrows and Enter`() {
        show()
        box().performTextInput("Hi @a")
        rule.mainClock.advanceTimeBy(SUGGEST_WAIT)
        rule.onNodeWithText("@ada").assertExists()
        box().performKeyInput { pressKey(Key.Enter) }
        assertEquals(did.toString(), "typed:Hi @ada ", did.last())
        assertTrue("enter" !in did)
    }

    @Test
    fun `CO-11 without the graphical composer, no buttons`() {
        rule.setContent {
            ComposerBox(
                ComposerState(),
                ada,
                "https://garden.idejo.test",
                events,
                graphical = false,
            )
        }
        rule.onNodeWithContentDescription("Send").assertDoesNotExist()
    }

    // The commands' dialogs hold text fields, which never go idle in a dialog under
    // Robolectric: their logic is tested in ComposerCommandsTest.
    @Test
    fun `CO-13 the exclamation mark lists the commands, Enter runs the one picked`() {
        val spoilers = mutableListOf<String?>()
        rule.setContent {
            var state by remember { mutableStateOf(ComposerState()) }
            ComposerBox(
                state,
                ada,
                "",
                ComposerEvents(
                    onTyped = { state = state.copy(text = it) },
                    commands =
                        CommandEvents(
                            commands = { listOf(Command("poll"), Command("spoiler")) },
                            onSpoiler = { spoilers += it },
                        ),
                ),
            )
        }
        box().performTextInput("!sp")
        rule.mainClock.advanceTimeBy(SUGGEST_WAIT)
        rule.onNodeWithText("!spoiler").assertExists()
        box().performKeyInput { pressKey(Key.Enter) }
        box().performTextInput("!spoiler:Book 3:")
        assertEquals(listOf(null, "Book 3"), spoilers)
    }
}

private const val SUGGEST_WAIT = 500L
