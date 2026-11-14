// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.markdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.idejo.app.core.designsystem.theme.ForumTheme
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Emoji
import org.idejo.app.core.model.Mention
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Message bodies in both themes; `./gradlew recordRoborazziDebug` records them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class MarkdownScreenshotTest {
    @Test
    fun `TV-20 TV-22 formats and inline links`() =
        snapshots("Formats") {
            MarkdownBody(
                IdejoMarkdown.parse(FORMATS, MENTIONS),
                actions =
                    MarkdownActions(emoji = {
                        if (it ==
                            "tada"
                        ) {
                            Emoji("tada", "🎉")
                        } else {
                            null
                        }
                    }),
                revealed = setOf(1),
            )
        }

    @Test
    fun `TV-20 TV-24 blocks`() = snapshots("Blocks") { MarkdownBody(IdejoMarkdown.parse(BLOCKS)) }

    @Test
    fun `TV-23 spoiler cover`() =
        snapshots("SpoilerCover") {
            SpoilerCover(revealed = false, onReveal = {}, overlay = "Season 2 ending") {
                MarkdownBody(
                    IdejoMarkdown.parse("The garden was **never** real. It was all a dream."),
                )
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
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.width(390.dp).padding(16.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
                        }
                    }
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

        val MENTIONS =
            listOf(
                Mention("user", "@robin", id = "u1", name = "robin"),
                Mention("role", "+Gardeners", "+gardeners", id = "gardeners"),
                Mention("topic", "#Garden plans", "#garden-plans", id = "t1"),
                Mention("category", "/Vegetables", "/vegetables", id = "c1"),
                Mention("tag", "\$Seeds", "\$seeds", id = "g1"),
                Mention("forum", "&Tennis club", "&tennis", id = "tennis.idejo.org"),
            )

        val FORMATS =
            """
            Thanks @robin and +gardeners! See #garden-plans in /vegetables, tagged ${'$'}seeds,
            or ask &tennis.

            **Bold**, *italic*, ~~struck~~, ==highlighted==, x^2^, `code` and a
            [link](https://idejo.org) :tada: :unknown:

            Hidden: ||the ending|| and shown: ||the start||.

            -# small print under it
            """.trimIndent()

        val BLOCKS =
            """
            ## Planting

            > Plant after the last frost.

            - [x] seeds
            - [ ] water
              1. morning
              2. evening

            ```
            fun water(plants: List<Plant>) = plants.forEach { it.water(litres = 2) } // long line
            ```

            ---

            | Plant | Weeks | Sun |
            |:------|:-----:|----:|
            | Tomato | 8 | full |
            | Lettuce | 4 | part |
            """.trimIndent()
    }
}
