// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.idejo.app.core.designsystem.theme.ForumTheme
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every component in both themes and two forum themes (Garden club's green, Tennis club's
 * blue), as the design canvas shows them. `./gradlew recordRoborazziDebug` records the
 * images in src/test/screenshots; `verifyRoborazziDebug` compares against them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class ComponentScreenshotTest {
    @Test
    fun `SH-01 SH-02 top bar with breadcrumbs`() =
        snapshots("TopBar", padded = false) {
            TopBarSample()
        }

    @Test
    fun `TH-05 SH-38 category marks, logo and picture`() =
        snapshots("CategoryMark") {
            CategoryMarkSample()
        }

    @Test
    fun `TH-05 idejo's category icons, every one`() =
        snapshots("CategoryIcons") {
            val assets = LocalContext.current.assets
            CategoryIconsSample(
                assets.list("category-icons").orEmpty().sorted().map {
                    it.removeSuffix(".svg")
                },
            )
        }

    @Test
    fun `SH-32 dots`() = snapshots("Dot") { DotSample() }

    @Test
    fun `NT-01 CO-05 bubbles`() = snapshots("Bubble") { BubbleSample() }

    @Test
    fun `BR-03 topic row compact`() =
        snapshots("TopicRowCompact") { TopicRowSample(expanded = false) }

    @Test
    fun `BR-03 topic row expanded`() =
        snapshots("TopicRowExpanded") { TopicRowSample(expanded = true) }

    @Test
    fun `SH-62 visible focus, a ring in the forum's colour`() =
        snapshots("FocusRing") {
            // What a keyboard's focus sends the theme's indication (touch mode takes none).
            val source = remember { MutableInteractionSource() }
            Text(
                "Tomato plans for spring",
                Modifier
                    .indication(source, LocalIndication.current)
                    .padding(12.dp),
            )
            LaunchedEffect(Unit) { source.emit(FocusInteraction.Focus()) }
        }

    @Test
    fun `TV-02 TH-05 message header`() = snapshots("MessageHeader") { MessageHeaderSample() }

    private fun snapshots(
        name: String,
        padded: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        val themes = listOf("garden" to SampleThemes.Garden, "tennis" to SampleThemes.Tennis)
        for ((themeName, theme) in themes) {
            for (dark in listOf(false, true)) {
                val mode = if (dark) "dark" else "light"
                val file = "src/test/screenshots/${name}_${themeName}_$mode.png"
                captureRoboImage(file, OPTIONS) { Frame(theme, dark, padded, content) }
            }
        }
    }

    @Composable
    private fun Frame(
        theme: ForumTheme,
        dark: Boolean,
        padded: Boolean,
        content: @Composable () -> Unit,
    ) {
        IdejoTheme(theme, dark) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(Modifier.width(390.dp).padding(if (padded) 16.dp else 0.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
                }
            }
        }
    }

    private companion object {
        // Small rendering differences between machines are not failures.
        val OPTIONS =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            )
    }
}
