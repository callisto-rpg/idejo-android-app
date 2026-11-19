// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.idejo.app.core.data.GroupForum
import org.idejo.app.core.designsystem.theme.ForumTheme
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** **Connect your forums** (SI-06). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class ConnectForumsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val garden = GroupForum("Garden club", "https://garden.example.org/", connected = true)
    private val bees = GroupForum("Bee keepers", "https://bees.example.org/")
    private val choir = GroupForum("Village choir", "https://choir.example.org/")
    private val state =
        ConnectForumsState(
            inboxName = "idejo",
            forums = listOf(garden, bees, choir),
            loading = false,
        )

    @Test
    fun `SI-06 each forum joined, connected or not, and Connect N forums`() {
        val events = mutableListOf<ConnectForumsEvent>()
        rule.setContent { ConnectForumsScreen(state, { events += it }) }
        rule.onAllNodesWithText("Not connected").assertCountEquals(2)
        rule.onNodeWithText("Connect 2 forums").performClick()
        rule.onAllNodesWithText("Connect")[0].performClick()
        rule.onNodeWithText("Garden club").performClick()
        assertEquals(
            listOf(
                ConnectForumsEvent.ConnectAll,
                ConnectForumsEvent.Connect(bees),
                ConnectForumsEvent.Enter(garden),
            ),
            events,
        )
    }

    @Test
    fun `IB-02 a connected forum's card says how many messages are new`() {
        rule.setContent {
            ConnectForumsScreen(state.copy(unread = mapOf(garden.url to 3, bees.url to 7)), {})
        }
        rule.onNodeWithText("3 new messages").assertExists()
        // Not connected: no count, Connect instead.
        rule.onNodeWithText("7 new messages").assertDoesNotExist()
    }

    @Test
    fun `IB-02 every forum connected, the cards alone`() {
        rule.setContent {
            ConnectForumsScreen(
                state.copy(
                    forums = listOf(garden, bees.copy(connected = true)),
                    unread =
                        mapOf(garden.url to 1),
                ),
                {},
            )
        }
        rule.onNodeWithText("Connect your forums").assertDoesNotExist()
        rule.onNodeWithText("1 new message").assertExists()
    }

    @Test
    fun `SI-06 a forum not yet connected cannot be opened`() {
        val events = mutableListOf<ConnectForumsEvent>()
        rule.setContent { ConnectForumsScreen(state, { events += it }) }
        rule.onNodeWithText("Bee keepers").performClick()
        assertEquals(emptyList<ConnectForumsEvent>(), events)
    }

    @Test
    fun `SI-07 a forum joined since last seen shows a Connect card`() {
        val events = mutableListOf<ConnectForumsEvent>()
        rule.setContent {
            ConnectForumsScreen(
                state.copy(fresh = setOf(choir.url)),
                { events += it },
            )
        }
        rule.onNodeWithText("You joined Village choir").assertExists()
        rule.onNodeWithText("Connect it to read it here.").assertExists()
        rule.onAllNodesWithText("Connect")[0].performClick()
        assertEquals(listOf<ConnectForumsEvent>(ConnectForumsEvent.Connect(choir)), events)
    }

    @Test
    fun `SI-07 a new forum once connected has no card`() {
        rule.setContent {
            ConnectForumsScreen(
                state.copy(
                    forums = listOf(garden, choir.copy(connected = true)),
                    fresh = setOf(choir.url),
                ),
                {},
            )
        }
        rule.onNodeWithText("You joined Village choir").assertDoesNotExist()
    }

    @Test
    fun `SI-07 screenshot`() =
        snapshots("ConnectForumsJoined") {
            ConnectForumsScreen(state.copy(fresh = setOf(choir.url)), {})
        }

    @Test
    fun `SI-06 screenshots`() {
        snapshots(
            "ConnectForums",
        ) { ConnectForumsScreen(state.copy(unread = mapOf(garden.url to 3)), {}) }
        snapshots("ConnectForumsConnecting") {
            ConnectForumsScreen(state.copy(connecting = bees.url), {})
        }
        snapshots("ConnectForumsNone") {
            ConnectForumsScreen(ConnectForumsState(inboxName = "idejo", loading = false), {})
        }
    }

    private fun snapshots(
        name: String,
        content: @Composable () -> Unit,
    ) {
        for (dark in listOf(false, true)) {
            val mode = if (dark) "dark" else "light"
            captureRoboImage("src/test/screenshots/${name}_$mode.png", OPTIONS) {
                IdejoTheme(ForumTheme.Default, dark) {
                    Box(Modifier.width(390.dp).height(520.dp)) { content() }
                }
            }
        }
    }

    private companion object {
        val OPTIONS =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            )
    }
}
