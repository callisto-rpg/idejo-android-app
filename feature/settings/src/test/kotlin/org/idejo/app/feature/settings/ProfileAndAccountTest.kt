// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.idejo.app.core.model.NotificationSettings
import org.idejo.app.core.model.Person
import org.idejo.app.feature.settings.deleteaccount.DeleteAccountEvent
import org.idejo.app.feature.settings.deleteaccount.DeleteAccountScreen
import org.idejo.app.feature.settings.deleteaccount.DeleteAccountUiState
import org.idejo.app.feature.settings.emailnotifications.EmailChoice
import org.idejo.app.feature.settings.emailnotifications.EmailNotificationsEvent
import org.idejo.app.feature.settings.emailnotifications.EmailNotificationsScreen
import org.idejo.app.feature.settings.emailnotifications.EmailNotificationsUiState
import org.idejo.app.feature.settings.ignoredusers.IgnoredUsersEvent
import org.idejo.app.feature.settings.ignoredusers.IgnoredUsersScreen
import org.idejo.app.feature.settings.ignoredusers.IgnoredUsersUiState
import org.idejo.app.feature.settings.messagecontrols.MessageControlsEvent
import org.idejo.app.feature.settings.messagecontrols.MessageControlsScreen
import org.idejo.app.feature.settings.messagecontrols.MessageControlsUiState
import org.idejo.app.feature.settings.writing.WritingEvent
import org.idejo.app.feature.settings.writing.WritingScreen
import org.idejo.app.feature.settings.writing.WritingUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Profile and account's overview and sections (ST-02, ST-03, ST-06, ST-08), and About (ST-23). */
@RunWith(RobolectricTestRunner::class)
class ProfileAndAccountTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `ST-02 the overview lists the sections under the web's tabs`() {
        rule.setContent { ProfileAndAccountScreen(ProfileAndAccountUiState(SETTINGS_SECTIONS), {}) }
        rule.onNodeWithText("Interface").assertExists()
        rule.onNodeWithText("Writing").assertExists()
        rule.onNodeWithText("Messages and notifications").assertExists()
        rule.onNodeWithText("Private messages").assertExists()
    }

    @Test
    fun `ST-03 ST-06 ST-08 the overview lists the new sections in the web's order`() {
        rule.setContent { ProfileAndAccountScreen(ProfileAndAccountUiState(SETTINGS_SECTIONS), {}) }
        rule.onNodeWithText("Message controls").assertExists()
        rule.onNodeWithText("Ignored users").assertExists()
        rule.onNodeWithText("Account").assertExists()
        rule.onNodeWithText("Delete account").assertExists()
        assertEquals(
            listOf(
                "writing",
                "message-controls",
                "private-messages",
                "ignored-users",
                "email-notifications",
                "delete-account",
            ),
            SETTINGS_SECTIONS.map { it.key },
        )
    }

    @Test
    fun `ST-03 a message controls switch sends the change`() {
        val events = mutableListOf<MessageControlsEvent>()
        rule.setContent {
            MessageControlsScreen(
                MessageControlsUiState(loading = false, onMobile = false, onDesktop = true),
                onEvent = { events += it },
            )
        }
        rule.onNodeWithText("Show message controls on desktop").assertIsOn()
        rule.onNodeWithText("Show message controls on mobile").assertIsOff().performClick()
        assertEquals(listOf<MessageControlsEvent>(MessageControlsEvent.OnMobile(true)), events)
    }

    @Test
    fun `ST-03 the email summary reads as the web and sends one choice`() {
        val events = mutableListOf<EmailNotificationsEvent>()
        rule.setContent {
            EmailNotificationsScreen(
                EmailNotificationsUiState(
                    loading = false,
                    username = "ada",
                    email = NotificationSettings.Email(mentions = true, newTopics = false),
                ),
                onEvent = { events += it },
            )
        }
        rule.onNodeWithText("@ada mentions and replies to my posts").assertIsOn()
        rule.onNodeWithText("Any activity in the forum").assertDoesNotExist()
        rule.onNodeWithText("New topics created").assertIsOff().performClick()
        assertEquals(
            listOf<EmailNotificationsEvent>(
                EmailNotificationsEvent.Set(EmailChoice.NewTopics, true),
            ),
            events,
        )
    }

    @Test
    fun `ST-03 a server without the email choices has no rows`() {
        rule.setContent {
            EmailNotificationsScreen(EmailNotificationsUiState(loading = false), {})
        }
        rule.onNodeWithText("New topics created").assertDoesNotExist()
    }

    @Test
    fun `ST-08 stop ignoring names the member and sends their id`() {
        val events = mutableListOf<IgnoredUsersEvent>()
        rule.setContent {
            IgnoredUsersScreen(
                IgnoredUsersUiState(
                    loading = false,
                    members = listOf(Person(id = "u_leo", displayName = "Leo Moreau")),
                ),
                onEvent = { events += it },
            )
        }
        rule.onNodeWithText("Leo Moreau").assertExists()
        rule.onNodeWithContentDescription("Stop ignoring Leo Moreau").performClick()
        assertEquals(listOf<IgnoredUsersEvent>(IgnoredUsersEvent.StopIgnoring("u_leo")), events)
    }

    @Test
    fun `ST-08 nobody ignored reads as the web`() {
        rule.setContent {
            IgnoredUsersScreen(IgnoredUsersUiState(loading = false, members = emptyList()), {})
        }
        rule.onNodeWithText("You ignore nobody.").assertExists()
    }

    @Test
    fun `ST-06 delete account opens the forum's page`() {
        val events = mutableListOf<DeleteAccountEvent>()
        val url = "https://garden.example.org/users/settings?tab=account#delete-heading"
        rule.setContent {
            DeleteAccountScreen(DeleteAccountUiState(url), onEvent = { events += it })
        }
        rule.onNodeWithText("Delete my account…").performClick()
        assertEquals(listOf<DeleteAccountEvent>(DeleteAccountEvent.Open(url)), events)
    }

    @Test
    fun `ST-06 without a deletion page there is no button`() {
        rule.setContent { DeleteAccountScreen(DeleteAccountUiState(), {}) }
        rule.onNodeWithText("Delete my account…").assertDoesNotExist()
    }

    @Test
    fun `ST-23 about shows the source code link and the privacy note`() {
        var opened = 0
        rule.setContent { AppSettingsScreen("0.0.1", emptyList(), onOpenSource = { opened++ }) }
        rule.onNodeWithText("Privacy").assertExists()
        rule.onNodeWithText("The app has no analytics", substring = true).assertExists()
        rule.onNodeWithText("Source code").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `ST-02 a switch shows the choice and sends the change`() {
        val events = mutableListOf<WritingEvent>()
        rule.setContent {
            WritingScreen(
                WritingUiState(loading = false, autoPunctuation = true, graphicalComposer = false),
                onEvent = { events += it },
            )
        }
        rule.onNodeWithText("Use the graphical composer").assertIsOff()
        rule.onNodeWithText("Automatic punctuation").assertIsOn().performClick()
        assertEquals(listOf<WritingEvent>(WritingEvent.AutoPunctuation(false)), events)
    }

    @Test
    fun `ST-02 a choice the server does not send has no row`() {
        rule.setContent {
            WritingScreen(WritingUiState(loading = false, autoPunctuation = true), onEvent = {})
        }
        rule.onNodeWithText("Use the graphical composer").assertDoesNotExist()
    }
}
