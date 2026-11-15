// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.idejo.app.core.designsystem.theme.ForumTheme
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Me
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import org.idejo.app.core.model.ComposerState as ServerComposer

/** The composer in both themes; `./gradlew recordRoborazziDebug` records. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h800dp-xhdpi")
class ComposerScreenshotTest {
    private val ada = Me("u_1", "Ada Lovelace", colour = "#8a6a1f")

    @Test
    fun `CO-04 CO-05 CO-08 the composer's states`() =
        snapshots("Composer") {
            Column {
                ComposerBox(ComposerState(), ada, "", ComposerEvents())
                ComposerBox(ComposerState(text = "Tomatoes need"), ada, "", ComposerEvents())
                ComposerBox(
                    ComposerState(
                        text = "Tomatoes need staking early. Which twine do you use",
                        sent = "Tomatoes need staking early.",
                        openMessageId = "m_1",
                    ),
                    ada,
                    "",
                    ComposerEvents(),
                )
                ComposerBox(ComposerState(committedId = "m_1"), ada, "", ComposerEvents())
                // CO-11: finished Markdown as it will look.
                ComposerBox(
                    ComposerState(
                        text = "# Seeds\n- **Sungold**, ~~Brandywine~~\n- ||a surprise||",
                        sent = "x",
                    ),
                    ada,
                    "",
                    ComposerEvents(),
                )
                ComposerBox(
                    ComposerState(text = "Again", error = ComposerError.Unreachable),
                    ada,
                    "",
                    ComposerEvents(),
                )
            }
        }

    @Test
    fun `CO-06 CO-07 slow mode's line and the tutorial bubbles`() {
        val now = Instant.now()
        val sent =
            ComposerState(
                text = "Tomatoes need staking early. ",
                sent = "Tomatoes need staking early.",
                openMessageId = "m_1",
                server =
                    ServerComposer(
                        openMessageId = "m_1",
                        mayStartNewAt = now.plusSeconds(10),
                        windowSeconds = 15,
                        unansweredSeconds = 600,
                        now = now,
                    ),
                serverAtMillis = now.toEpochMilli(),
                openedAt = now.minusSeconds(5),
            )
        snapshots("ComposerSlowMode") {
            Column {
                ComposerBox(sent, ada, "", ComposerEvents())
                ComposerBox(sent.copy(text = sent.text + "Which"), ada, "", ComposerEvents())
            }
        }
    }

    private fun snapshots(
        name: String,
        content: @Composable () -> Unit,
    ) {
        for (dark in listOf(false, true)) {
            val mode = if (dark) "dark" else "light"
            captureRoboImage("src/test/screenshots/${name}_$mode.png", OPTIONS) {
                IdejoTheme(GARDEN, dark) {
                    Surface(color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
    }

    private companion object {
        val GARDEN = ForumTheme.fromHex("#3f7d3a", "#8a6a1f", "#2c6a8e", "#f7faf2", "#0b1410")!!
        val OPTIONS =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            )
    }
}
