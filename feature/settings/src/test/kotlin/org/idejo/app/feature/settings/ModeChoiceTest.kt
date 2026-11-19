// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.idejo.app.core.data.AppMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** App settings → **Mode** (MD-04). */
@RunWith(RobolectricTestRunner::class)
class ModeChoiceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<AppSettingsEvent>()
    private var signIn = 0

    private fun mode(state: AppSettingsUiState) =
        rule.setContent {
            ModeChoice(requireNotNull(state.mode), state, { events += it }) { signIn++ }
        }

    @Test
    fun `MD-04 from one forum, idejo ID is its sign-in`() {
        mode(AppSettingsUiState(mode = AppMode.Single))
        rule.onNodeWithText("idejo ID: your inbox and all your forums").performClick()
        assertEquals(1, signIn)
        assertEquals(emptyList<AppSettingsEvent>(), events)
    }

    @Test
    fun `MD-04 leaving idejo ID mode names what is signed out of, after a confirmation`() {
        mode(
            AppSettingsUiState(
                mode = AppMode.IdejoId,
                forums =
                    listOf(
                        HeldForum("https://garden.example.org/", "Garden club"),
                        HeldForum("https://bees.example.org/", "Bee keepers"),
                    ),
                inboxName = "idejo",
                current = "https://garden.example.org/",
            ),
        )
        rule.onNodeWithText("One forum").performClick()
        rule
            .onNodeWithText(
                "You will be signed out of idejo, Bee keepers on this device, and the app forgets them.",
            ).assertExists()
        rule.onNodeWithText("Bee keepers").performClick()
        rule
            .onNodeWithText(
                "You will be signed out of idejo, Garden club on this device, and the app forgets them.",
            ).assertExists()
        rule.onNodeWithText("Keep one forum").performClick()
        assertEquals(listOf(AppSettingsEvent.LeaveIdejoId("https://bees.example.org/")), events)
    }

    @Test
    fun `MD-04 Cancel leaves the mode as it is`() {
        mode(AppSettingsUiState(mode = AppMode.IdejoId, inboxName = "idejo"))
        rule.onNodeWithText("One forum").performClick()
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList<AppSettingsEvent>(), events)
    }
}
