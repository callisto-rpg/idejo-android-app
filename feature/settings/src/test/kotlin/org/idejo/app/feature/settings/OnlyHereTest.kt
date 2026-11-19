// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.idejo.app.core.data.InboxShortcut
import org.idejo.app.core.data.InboxShortcuts
import org.idejo.app.core.data.Launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Linked members' settings (ST-07): this forum's only; the inbox's for all forums. */
@RunWith(RobolectricTestRunner::class)
class OnlyHereTest {
    @get:Rule val forum = SignedInForum()

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val shortcuts = InboxShortcuts()

    private fun viewModel() = ProfileAndAccountViewModel(forum.sessions, forum.group, shortcuts)

    @Test
    fun `ST-07 a forum of the member's idejo ID says its settings apply only there`() {
        forum.linked()
        val model = viewModel()
        assertEquals("Garden club", model.onlyHere.until { it != null })

        model.allForums()
        eventually { forum.sessions.launch.value is Launch.Inbox }
        assertEquals(InboxShortcut.Settings, shortcuts.take())
    }

    @Test
    fun `ST-07 single-forum mode has no such note`() {
        forum.signIn()
        assertNull(viewModel().onlyHere.value)
    }

    @Test
    fun `ST-07 the note and Settings for all forums`() {
        var asked = 0
        rule.setContent {
            ProfileAndAccountScreen(
                ProfileAndAccountUiState(SETTINGS_SECTIONS, onlyHere = "Garden club"),
                onEvent = { if (it == ProfileAndAccountEvent.AllForums) asked++ },
            )
        }
        rule
            .onNodeWithText(
                "These settings apply only to Garden club. To change them for all your forums " +
                    "at once, use your settings for all forums.",
            ).assertExists()
        rule.onNodeWithText("Settings for all forums").performClick()
        assertEquals(1, asked)
    }
}
