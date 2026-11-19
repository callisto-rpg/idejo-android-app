// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
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

/** The inbox in pictures (IB-01, IB-03), light and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class InboxScreenshotTest {
    private val state = InboxSamples.state

    @Test
    fun `IB-01 IB-03 Topics, the counts under each row`() =
        snapshots("InboxTopics") {
            InboxScreen(
                state,
                forums = {},
                topics = { TopicsTab(state, {}) },
                chats = {},
                initialTab = InboxTab.Topics,
            )
        }

    @Test
    @Config(qualifiers = "w840dp-h640dp-mdpi")
    fun `IB-03 the counts at the right on wide screens`() =
        snapshots("InboxTopicsWide", width = 840.dp) {
            InboxScreen(
                state,
                forums = {},
                topics = { TopicsTab(state, {}) },
                chats = {},
                initialTab = InboxTab.Topics,
            )
        }

    private fun snapshots(
        name: String,
        width: Dp = 390.dp,
        content: @Composable () -> Unit,
    ) {
        for (dark in listOf(false, true)) {
            val mode = if (dark) "dark" else "light"
            captureRoboImage("src/test/screenshots/${name}_$mode.png", OPTIONS) {
                IdejoTheme(ForumTheme.Default, dark) {
                    Box(Modifier.width(width).height(640.dp)) { content() }
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
