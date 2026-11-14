// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.data.Access
import org.idejo.app.core.data.SignInState
import org.idejo.app.core.designsystem.theme.ForumTheme
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.ApiInfo
import org.idejo.app.core.model.Discovery
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The first launch in idejo's own theme (MD-01, TH-08), light and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class OnboardingScreenshotTest {
    private val garden =
        DiscoveryResult.Found(
            (ForumAddress.parse("garden.example.org") as ForumAddress.Parsed.Valid).address,
            Discovery(name = "Garden club", url = "https://garden.example.org/", api = ApiInfo()),
        )

    @Test
    fun `MD-01 welcome`() = snapshots("Welcome") { WelcomeScreen(onEnterAddress = {}) }

    @Test
    fun `SI-01 address field`() = snapshots("AddForum") { Add(AddForumState.Typing()) }

    @Test
    fun `SI-01 looking for the forum`() =
        snapshots("AddForumLooking") { Add(AddForumState.Looking) }

    @Test
    fun `SI-02 not an idejo forum`() =
        snapshots("AddForumNotIdejo") { Add(AddForumState.Typing(AddForumState.Problem.NotIdejo)) }

    @Test
    fun `SI-02 the app is turned off`() =
        snapshots("AddForumAppOff") {
            Add(AddForumState.Typing(AddForumState.Problem.AppTurnedOff("Garden club")))
        }

    @Test
    fun `SI-01 SI-05 the forum found, sign in or read as a visitor`() =
        snapshots("AddForumFound") { Add(AddForumState.Found(garden)) }

    @Test
    fun `SI-03 waiting for the browser`() =
        snapshots("AddForumWaiting") {
            Add(AddForumState.Found(garden), SignInState.Waiting("Garden club"))
        }

    @Test
    fun `SI-09 signed out`() =
        snapshots("SignedOut", theme = GARDEN) {
            AccessNotice(Access.SignedOut, "Garden club", onSignInAgain = {})
        }

    @Test
    fun `SI-10 banned`() =
        snapshots("Barred", theme = GARDEN) {
            AccessNotice(
                Access.Barred("You are banned from Garden club: spam.", null),
                "Garden club",
                {},
            )
        }

    @Composable
    private fun Add(
        state: AddForumState,
        signIn: SignInState = SignInState.Idle,
    ) = AddForumScreen(state, signIn, {}, {}, {}, {}, {})

    private fun snapshots(
        name: String,
        theme: ForumTheme = ForumTheme.Default,
        content: @Composable () -> Unit,
    ) {
        for (dark in listOf(false, true)) {
            val mode = if (dark) "dark" else "light"
            captureRoboImage("src/test/screenshots/${name}_$mode.png", OPTIONS) {
                IdejoTheme(theme, dark) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(
                            Modifier.width(390.dp).padding(
                                if (theme ==
                                    GARDEN
                                ) {
                                    16.dp
                                } else {
                                    0.dp
                                },
                            ),
                        ) { content() }
                    }
                }
            }
        }
    }

    private companion object {
        val GARDEN =
            ForumTheme.fromHex("#3f6b3a", "#8a6d3b", "#b5523b", "#f6f3ea", "#1d211b")
        val OPTIONS =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            )
    }
}
