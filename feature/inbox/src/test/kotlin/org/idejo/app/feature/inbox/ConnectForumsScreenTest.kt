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
        rule.onNodeWithText("Connected").assertExists()
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
    fun `SI-06 a forum not yet connected cannot be opened`() {
        val events = mutableListOf<ConnectForumsEvent>()
        rule.setContent { ConnectForumsScreen(state, { events += it }) }
        rule.onNodeWithText("Bee keepers").performClick()
        assertEquals(emptyList<ConnectForumsEvent>(), events)
    }

    @Test
    fun `SI-06 screenshots`() {
        snapshots("ConnectForums") { ConnectForumsScreen(state, {}) }
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
