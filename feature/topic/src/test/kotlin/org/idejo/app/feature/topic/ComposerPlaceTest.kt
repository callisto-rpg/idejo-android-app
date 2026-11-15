// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.idejo.app.core.data.TopicState
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.MessageState
import org.idejo.app.core.model.Person
import org.idejo.app.core.model.Topic
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Where the composer stands in the topic (CO-01). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h1400dp-xhdpi")
class ComposerPlaceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val ada = Person("u_ada", "Ada Lovelace")
    private val leo = Person("u_leo", "Leo Moreau")
    private val messages =
        listOf(
            Message("m1", "t_1", leo, MessageState.Closed, bodyMd = "Sungold every year."),
            Message("m2", "t_1", ada, MessageState.Open, bodyMd = "Which twine do you use?"),
            Message("m3", "t_1", leo, MessageState.Closed, bodyMd = "Jute, soft on the stems."),
        )
    private val topic =
        TopicState(topic = Topic("t_1", "Tomato plans"), messages = messages, loading = false)

    private fun show(writing: TopicWriting) =
        rule.setContent { TopicScreen(TopicUi(topic), TopicActions(), writing = writing) }

    private fun writing(open: String?) =
        TopicWriting(openMessageId = open, mayWrite = true, box = { Text("COMPOSER") })

    @Test
    fun `CO-01 the composer stands in place of the author's open message`() {
        show(writing("m2"))
        rule.onNodeWithText("COMPOSER").assertExists()
        rule.onNodeWithText("Which twine do you use?").assertDoesNotExist()
        rule.onNodeWithText("Jute, soft on the stems.").assertExists()
        assertFalse(composerAtEnd(topic, writing("m2")))
    }

    @Test
    fun `CO-01 with no open message, the composer sits at the end once reached`() {
        show(writing(null))
        rule.onNodeWithText("COMPOSER").assertExists()
        assertTrue(composerAtEnd(topic, writing(null)))
        assertFalse(composerAtEnd(topic.copy(later = 3), writing(null)))
        assertFalse(composerAtEnd(topic, writing(null).copy(mayWrite = false)))
    }
}
