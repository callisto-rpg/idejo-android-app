// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** SH-01, TV-02: forum logos and profile pictures sit on the surface around them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ForumLogoTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun centreOf(tag: String): Color {
        val pixels = rule.onNodeWithTag(tag).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    @Test
    fun `SH-01 without a logo, the initial on the primary colour`() {
        var primary = Color.Unspecified
        rule.setContent {
            IdejoTheme {
                primary = MaterialTheme.colorScheme.primary
                ForumLogo("Garden club", Modifier.testTag("logo"))
            }
        }
        assertEquals(primary, centreOf("logo"))
    }

    @Test
    fun `SH-01 a logo that cannot be read leaves the initial, never nothing`() {
        var primary = Color.Unspecified
        rule.setContent {
            IdejoTheme {
                primary = MaterialTheme.colorScheme.primary
                ForumLogo(
                    "Garden club",
                    Modifier.testTag("logo"),
                    logoUrl = "https://invalid.test/x.svg",
                )
            }
        }
        rule.waitForIdle()
        assertEquals(primary, centreOf("logo"))
    }

    @Test
    fun `TV-02 a transparent profile picture shows the background it is placed on`() {
        val surface = Color(0xFFF5F2EA)
        rule.setContent {
            IdejoTheme {
                Box(Modifier.background(surface)) {
                    Avatar(
                        "Ada Lovelace",
                        Color(0xFFF5C518),
                        Modifier.testTag("avatar"),
                        image = {},
                    )
                }
            }
        }
        val pixels = rule.onNodeWithTag("avatar").captureToImage().toPixelMap()
        assertEquals(surface, pixels[pixels.width / 2, pixels.height / 2])
    }
}
