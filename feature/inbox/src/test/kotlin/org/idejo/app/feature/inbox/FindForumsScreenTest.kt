// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.idejo.app.core.designsystem.theme.ForumTheme
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.DirectoryForum
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** **Find more forums** (SI-08). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class FindForumsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val garden =
        DirectoryForum(
            "Garden club",
            "https://garden.example.org/",
            "garden.example.org",
            members = 128,
        )
    private val bees =
        DirectoryForum("Bee keepers", "https://bees.example.org/", "bees.example.org", members = 1)
    private val state =
        FindForumsState(
            forums = listOf(garden, bees),
            page = 1,
            more = true,
            loading = false,
            held = setOf("garden.example.org"),
        )

    @Test
    fun `SI-08 each forum with its address and members, Join or Open`() {
        val events = mutableListOf<FindForumsEvent>()
        rule.setContent { FindForumsScreen(state, { events += it }, {}) }
        rule.onNodeWithText("garden.example.org").assertExists()
        rule.onNodeWithText("128 members").assertExists()
        rule.onNodeWithText("1 member").assertExists()
        rule.onNodeWithText("Open").performClick()
        rule.onNodeWithText("Join").performClick()
        rule.onNodeWithText("More forums").performClick()
        assertEquals(
            listOf(FindForumsEvent.Open(garden), FindForumsEvent.Join(bees), FindForumsEvent.More),
            events,
        )
    }

    @Test
    fun `SI-08 the directory is searchable`() {
        val events = mutableListOf<FindForumsEvent>()
        rule.setContent { FindForumsScreen(state, { events += it }, {}) }
        rule.onNodeWithText("Search by name or address").performTextInput("bee")
        assertEquals(listOf<FindForumsEvent>(FindForumsEvent.Search("bee")), events)
    }

    @Test
    fun `SI-08 nothing found, and the directory not answering`() {
        rule.setContent {
            FindForumsScreen(FindForumsState(query = "choir", loading = false), {}, {})
        }
        rule.onNodeWithText("No forum matches “choir”.").assertExists()
    }

    @Test
    fun `SI-06 SI-08 Find more forums from the inbox's list`() {
        var found = 0
        rule.setContent {
            ConnectForumsScreen(
                ConnectForumsState(inboxName = "idejo", loading = false, directory = true),
                {},
                onFindMore = { found++ },
            )
        }
        rule.onNodeWithText("Find more forums").performClick()
        assertEquals(1, found)
    }

    @Test
    fun `SI-08 screenshots`() {
        snapshots("FindForums") { FindForumsScreen(state, {}, {}) }
        snapshots("FindForumsJoining") { FindForumsScreen(state.copy(joining = bees.host), {}, {}) }
        snapshots("FindForumsFailed") {
            FindForumsScreen(FindForumsState(loading = false, failed = true), {}, {})
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
                    Box(Modifier.width(390.dp).height(600.dp)) { content() }
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
