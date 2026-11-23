// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.MessageState
import org.idejo.app.core.model.Person
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A message's body on the page (TV-09). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class MessageItemTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(body: String) =
        rule.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                MessageItem(
                    Message(
                        "m_1",
                        "t_1",
                        Person("u_leo", "Leo Moreau"),
                        MessageState.Closed,
                        bodyMd = body,
                    ),
                    MessageContext("https://garden.example"),
                )
            }
        }

    @Test
    fun `TV-09 a body longer than the screen shows collapsed with Show all`() {
        show((1..120).joinToString("\n\n") { "Line $it of a very long message." })
        rule
            .onNodeWithText("Show all")
            .assertExists()
            .performScrollTo()
            .performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Show all").assertDoesNotExist()
    }

    @Test
    fun `TV-09 a short body shows whole`() {
        show("Planning this year's beds.")
        rule.onNodeWithText("Planning this year's beds.").assertExists()
        rule.onNodeWithText("Show all").assertDoesNotExist()
    }

    @Test
    fun `MA-01 a tapped message's controls take the space under it, and as much again`() {
        var open by mutableStateOf<String?>(null)
        rule.setContent {
            MessageItem(
                Message(
                    "m_1",
                    "t_1",
                    Person("u_leo", "Leo Moreau"),
                    MessageState.Closed,
                    bodyMd = "Planning this year's beds.",
                ),
                MessageContext(
                    "https://garden.example",
                    actions = MessageActions(onReply = {}),
                    actionsOpen = open,
                ),
                Modifier.testTag("message"),
            )
        }
        val closed = rule.onNodeWithTag("message").getUnclippedBoundsInRoot().run { bottom - top }
        open = "m_1"
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Reply").assertExists()
        assertEquals(
            closed + 24.dp,
            rule.onNodeWithTag("message").getUnclippedBoundsInRoot().run { bottom - top },
        )
    }
}
