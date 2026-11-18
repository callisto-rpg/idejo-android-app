// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.idejo.app.core.data.PushStatus
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Me
import org.idejo.app.core.model.NotificationSettings
import org.idejo.app.core.model.Person
import org.idejo.app.core.model.PushRegistration
import org.idejo.app.core.model.Token
import org.idejo.app.feature.settings.appsdevices.AppsDevicesScreen
import org.idejo.app.feature.settings.appsdevices.AppsDevicesUiState
import org.idejo.app.feature.settings.deleteaccount.DeleteAccountScreen
import org.idejo.app.feature.settings.deleteaccount.DeleteAccountUiState
import org.idejo.app.feature.settings.emailnotifications.EmailNotificationsScreen
import org.idejo.app.feature.settings.emailnotifications.EmailNotificationsUiState
import org.idejo.app.feature.settings.ignoredusers.IgnoredUsersScreen
import org.idejo.app.feature.settings.ignoredusers.IgnoredUsersUiState
import org.idejo.app.feature.settings.messagecontrols.MessageControlsScreen
import org.idejo.app.feature.settings.messagecontrols.MessageControlsUiState
import org.idejo.app.feature.settings.password.PasswordScreen
import org.idejo.app.feature.settings.password.PasswordUiState
import org.idejo.app.feature.settings.privatemessages.PrivateMessagesScreen
import org.idejo.app.feature.settings.privatemessages.PrivateMessagesUiState
import org.idejo.app.feature.settings.profile.ProfileScreen
import org.idejo.app.feature.settings.profile.ProfileUiState
import org.idejo.app.feature.settings.pushnotifications.PushNotificationsScreen
import org.idejo.app.feature.settings.pushnotifications.PushNotificationsUiState
import org.idejo.app.feature.settings.writing.WritingScreen
import org.idejo.app.feature.settings.writing.WritingUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** Profile and account's overview and sections, light and dark (ST-02, ST-03, ST-06, ST-08). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class ProfileAndAccountScreenshotTest {
    @Test
    fun `ST-02 ST-03 ST-06 ST-08 PC-03 the overview and its sections`() {
        shoot("ProfileAndAccount") {
            ProfileAndAccountScreen(ProfileAndAccountUiState(SETTINGS_SECTIONS), {})
        }
        shoot("Writing") {
            WritingScreen(
                WritingUiState(loading = false, autoPunctuation = true, graphicalComposer = false),
                {},
            )
        }
        shoot("PrivateMessages") {
            PrivateMessagesScreen(
                PrivateMessagesUiState(loading = false, acceptsPrivateMessages = true),
                {},
            )
        }
        shoot("DeleteAccount") {
            DeleteAccountScreen(
                DeleteAccountUiState("https://garden.example.org/users/settings?tab=account"),
                {},
            )
        }
    }

    @Test
    fun `ST-03 ST-08 the Messages and notifications sections`() {
        shoot("MessageControls") {
            MessageControlsScreen(
                MessageControlsUiState(loading = false, onMobile = true, onDesktop = false),
                {},
            )
        }
        shoot("IgnoredUsers") {
            IgnoredUsersScreen(
                IgnoredUsersUiState(
                    loading = false,
                    members =
                        listOf(
                            Person(id = "u_leo", displayName = "Leo Moreau"),
                            Person(id = "u_mina", displayName = "Mina Kader", colour = "#6a3e8e"),
                        ),
                ),
                {},
            )
        }
        shoot("EmailNotifications") {
            EmailNotificationsScreen(
                EmailNotificationsUiState(
                    loading = false,
                    username = "ada",
                    email =
                        NotificationSettings.Email(
                            mentions = true,
                            roleMentions = false,
                            ownTopics = true,
                            followedTopics = false,
                            newTopics = false,
                            allActivity = false,
                            onceAWeek = true,
                        ),
                ),
                {},
            )
        }
    }

    @Test
    fun `ST-01 profile`() {
        shoot("Profile") {
            ProfileScreen(
                ProfileUiState(
                    loading = false,
                    me = Me(id = "u_ada", displayName = "Ada Lovelace", userTag = "ada"),
                    name = "Ada Lovelace",
                    tag = "ada",
                    bio = "Analyst of **engines**.",
                ),
                {},
            )
        }
    }

    @Test
    fun `NT-08 push notifications`() {
        shoot("PushNotifications") {
            PushNotificationsScreen(
                PushNotificationsUiState(
                    loading = false,
                    username = "ada",
                    push = NotificationSettings.Push(true, true, true, false, true),
                    devices =
                        listOf(
                            PushRegistration(
                                "p_1",
                                "unifiedpush",
                                "Pixel 8",
                                thisDevice = true,
                                createdAt = NOON,
                            ),
                            PushRegistration("p_2", "web", "Firefox on Linux", createdAt = NOON),
                        ),
                ),
                {},
            )
        }
    }

    @Test
    fun `NT-06 push without a distributor`() {
        shoot("PushNoDistributor") {
            PushNotificationsScreen(
                PushNotificationsUiState(
                    loading = false,
                    username = "ada",
                    push = NotificationSettings.Push(true, true, true, false, true),
                    devices = emptyList(),
                    device = PushStatus.NoDistributor,
                ),
                {},
            )
        }
    }

    @Test
    fun `ST-04 change password`() {
        shoot("Password") { PasswordScreen(PasswordUiState(current = "secret"), {}) }
    }

    @Test
    fun `ST-05 the Account sections`() {
        shoot("AppsDevices") {
            AppsDevicesScreen(
                AppsDevicesUiState(
                    loading = false,
                    devices =
                        listOf(
                            Token("t_1", "Pixel 8", signedInAt = NOON, current = true),
                            Token(
                                "t_2",
                                "Galaxy Tab",
                                signedInAt = NOON,
                                lastUsedAt = NOON,
                            ),
                        ),
                ),
                {},
            )
        }
    }

    private fun shoot(
        name: String,
        content: @Composable () -> Unit,
    ) {
        for (dark in listOf(false, true)) {
            val mode = if (dark) "dark" else "light"
            captureRoboImage("src/test/screenshots/${name}_$mode.png", OPTIONS) {
                IdejoTheme(darkTheme = dark) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.width(390.dp)) { content() }
                    }
                }
            }
        }
    }

    private companion object {
        /** Midday, so the date is the same in every zone a test runs in. */
        val NOON: Instant = Instant.parse("2026-11-10T12:00:00Z")

        val OPTIONS =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            )
    }
}
