// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.idejo.app.core.designsystem.component.Crumb
import org.idejo.app.core.designsystem.component.TopBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A forum joined since last seen (SI-07): the logo's dot, **Your forums** (MD-02). */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
class NewForumsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `SI-07 the logo says a joined forum waits to be connected`() {
        rule.setContent { TopBar(listOf(Crumb.Forum("Garden club", {}, dot = true))) }
        rule
            .onNodeWithContentDescription(
                "Garden club, a forum you joined is waiting to be connected",
            ).assertExists()
    }

    @Test
    fun `SI-07 without a new forum, the logo is the menu only`() {
        rule.setContent { TopBar(listOf(Crumb.Forum("Garden club", {}))) }
        rule.onNodeWithContentDescription("Garden club").assertExists()
    }

    @Test
    fun `SI-07 a forum joined since, with its dot and Connect, in Your forums`() {
        val connected = mutableListOf<String>()
        rule.setContent {
            LogoMenu(
                "Garden club",
                "garden.example.org",
                {},
                {},
                group =
                    GroupMenu(
                        listOf(
                            YourForum(
                                "https://bees.example.org/",
                                "Bee keepers",
                                held = false,
                                connect = true,
                                new = true,
                            ),
                        ),
                    ),
                actions = RecordedActions(connected),
            )
        }
        rule
            .onNodeWithContentDescription(
                "A forum you joined is waiting to be connected",
            ).assertExists()
        rule.onNodeWithText("Connect").performScrollTo().performClick()
        assertEquals(listOf("connect https://bees.example.org/"), connected)
    }

    @Test
    fun `MD-03 single-forum mode keeps its note and offers idejo ID mode`() {
        val done = mutableListOf<String>()
        rule.setContent {
            LogoMenu(
                "Garden club",
                "garden.example.org",
                {},
                {},
                actions = RecordedActions(done),
            )
        }
        rule.onNodeWithText("Add a forum").assertExists()
        rule.onNodeWithText("Your forums").assertDoesNotExist()
        rule.onNodeWithText("Sign in with idejo ID").performScrollTo().performClick()
        assertEquals(listOf("idejo ID"), done)
    }
}

/** The logo menu's actions, written down as they happen. */
internal class RecordedActions(
    private val done: MutableList<String>,
) : LogoMenuActions {
    override fun enter(forum: YourForum) {
        done += "enter ${forum.origin}"
    }

    override fun connect(forum: YourForum) {
        done += "connect ${forum.origin}"
    }

    override fun remove(forum: YourForum) {
        done += "remove ${forum.origin}"
    }

    override fun openInBrowser(forum: YourForum) {
        done += "browser ${forum.origin}"
    }

    override fun topics() {
        done += "topics"
    }

    override fun chats() {
        done += "chats"
    }

    override fun findForums() {
        done += "find"
    }

    override fun addByAddress() {
        done += "address"
    }

    override fun idejoId() {
        done += "idejo ID"
    }
}
