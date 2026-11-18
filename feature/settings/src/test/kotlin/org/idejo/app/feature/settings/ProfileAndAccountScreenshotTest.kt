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
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Person
import org.idejo.app.feature.settings.deleteaccount.DeleteAccountScreen
import org.idejo.app.feature.settings.deleteaccount.DeleteAccountUiState
import org.idejo.app.feature.settings.ignoredusers.IgnoredUsersScreen
import org.idejo.app.feature.settings.ignoredusers.IgnoredUsersUiState
import org.idejo.app.feature.settings.messagecontrols.MessageControlsScreen
import org.idejo.app.feature.settings.messagecontrols.MessageControlsUiState
import org.idejo.app.feature.settings.privatemessages.PrivateMessagesScreen
import org.idejo.app.feature.settings.privatemessages.PrivateMessagesUiState
import org.idejo.app.feature.settings.writing.WritingScreen
import org.idejo.app.feature.settings.writing.WritingUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
        shoot("DeleteAccount") {
            DeleteAccountScreen(
                DeleteAccountUiState("https://garden.example.org/users/settings?tab=account"),
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
        val OPTIONS =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            )
    }
}
