// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.idejo.app.core.data.TopicState
import org.idejo.app.core.model.Emoji
import org.idejo.app.core.model.Me
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.MessageState
import org.idejo.app.core.model.Person
import org.idejo.app.core.model.Reaction
import org.idejo.app.core.model.Reactor
import org.idejo.app.core.model.Topic
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A message's actions (MA-01 to MA-07). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w600dp-h1400dp-xhdpi")
class MessageActionsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val ada = Person("u_ada", "Ada Lovelace")
    private val leo = Person("u_leo", "Leo Moreau")
    private val tada = Reaction(":tada:", "tada", 2, char = "🎉")
    private val leos =
        Message(
            "m1",
            "t_1",
            leo,
            MessageState.Closed,
            bodyMd = "Sungold every year.",
            url = "https://garden.example/m/m1",
            reactions = listOf(tada),
        )
    private val adas =
        Message("m2", "t_1", ada, MessageState.Closed, bodyMd = "Jute, soft on the stems.")
    private val topic =
        TopicState(
            topic = Topic("t_1", "Tomato plans", categoryId = "c_veg"),
            messages = listOf(leos, adas),
            loading = false,
        )
    private val me = Me("u_ada", "Ada Lovelace")
    private val done = mutableListOf<String>()

    private fun show(
        me: Me = this.me,
        actions: TopicActions = TopicActions(),
    ) = rule.setContent {
        TopicScreen(
            TopicUi(topic, signedIn = true, me = me, quick = listOf(Emoji("heart", char = "❤️"))),
            actions,
            writing =
                TopicWriting(
                    mayWrite = true,
                    onReply = { m, _ -> done += "reply ${m.id}" },
                    onForward = { done += "forward ${it.id}" },
                    onEdit = { done += "edit ${it.id}" },
                    onNewTopic = { m, category -> done += "new ${m.id} in $category" },
                ),
        )
    }

    @Test
    fun `MA-01 a tapped message shows its actions, tapped again they close`() {
        show()
        rule.onNodeWithText("Sungold every year.").performClick()
        listOf(
            "React",
            "Reply",
            "Forward",
            "New Topic",
            "Report",
            "Copy text",
            "Copy link",
            "Share",
        ).forEach { rule.onNodeWithContentDescription(it).assertExists() }
        // Leo's message: not the member's to edit.
        rule.onNodeWithContentDescription("Edit").assertDoesNotExist()
        rule.onNodeWithContentDescription("Reply").performClick()
        assertEquals(listOf("reply m1"), done)
        rule.onNodeWithContentDescription("Reply").assertDoesNotExist()
    }

    @Test
    fun `MA-01 the member's own message is edited, never reported`() {
        show()
        rule.onNodeWithText("Jute, soft on the stems.").performClick()
        rule.onNodeWithContentDescription("Report").assertDoesNotExist()
        rule.onNodeWithContentDescription("Edit").performClick()
        assertEquals(listOf("edit m2"), done)
    }

    @Test
    fun `MA-01 show message controls puts the actions under every message`() {
        show(me.copy(controlsOnMobile = true))
        rule.onAllNodes(hasContentDescription("Copy text")).let { nodes ->
            assertEquals(2, nodes.fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `MA-02 when the actions do not fit, Reply, Forward and New Topic go under Quote`() {
        rule.setContent {
            ActionsBar(
                leos,
                MessageActions(onReact = {
                    _,
                    _,
                    _,
                    ->
                }, onReply = {}, onForward = {}, onNewTopic = {}, onReport = {
                    _,
                    _,
                    ->
                    true
                }),
                "",
                Modifier.width(320.dp),
            )
        }
        rule.onNodeWithContentDescription("Reply").assertDoesNotExist()
        rule.onNodeWithContentDescription("Quote").performClick()
        rule.onNodeWithText("Forward").assertExists()
        rule.onNodeWithText("New Topic").assertExists()
    }

    @Test
    fun `MA-03 React gives quick choices, a reaction tapped toggles, long-pressed says who`() {
        val reacted = mutableListOf<Triple<String, String, Boolean>>()
        show(
            actions =
                TopicActions(
                    onReact = { id, name, on -> reacted += Triple(id, name, on) },
                    reactors = {
                        listOf(
                            Reactor(leo, listOf(":tada:")),
                            Reactor(ada, listOf(":tada:")),
                        )
                    },
                ),
        )
        rule.onNodeWithText("Sungold every year.").performClick()
        rule.onNodeWithContentDescription("React").performClick()
        rule.onNodeWithContentDescription(":heart:").performClick()
        assertEquals(Triple("m1", "heart", true), reacted.last())

        rule.onNodeWithContentDescription("🎉 2 reactions", substring = true).performClick()
        assertEquals(Triple("m1", "tada", true), reacted.last())
        rule.onNodeWithContentDescription("🎉 2 reactions", substring = true).performTouchInput {
            longClick()
        }
        rule.onNodeWithText("Who reacted").assertExists()
        rule.onNodeWithText("Leo Moreau  :tada:").assertExists()
    }

    @Test
    fun `MA-05 New Topic starts from the message, in the topic's category`() {
        show()
        rule.onNodeWithText("Sungold every year.").performClick()
        rule.onNodeWithContentDescription("New Topic").performClick()
        assertEquals(listOf("new m1 in c_veg"), done)
    }

    @Test
    fun `MA-07 a long-press on the text selects it and opens no actions`() {
        show()
        rule.onNodeWithText("Sungold every year.").performTouchInput { longClick() }
        rule.onNodeWithContentDescription("Reply").assertDoesNotExist()
        rule.onNodeWithText("Sungold every year.").assertExists()
    }

    @Test
    fun `PC-01 a private conversation has no Follow and no New Topic`() {
        rule.setContent {
            TopicScreen(
                TopicUi(
                    topic.copy(topic = topic.topic?.copy(private = true, categoryId = null)),
                    signedIn = true,
                    me = me,
                ),
                TopicActions(),
                writing = TopicWriting(mayWrite = true),
            )
        }
        rule.onNodeWithText("Follow").assertDoesNotExist()
        rule.onNodeWithText("Sungold every year.").performClick()
        rule.onNodeWithContentDescription("Reply").assertExists()
        rule.onNodeWithContentDescription("New Topic").assertDoesNotExist()
    }
}
