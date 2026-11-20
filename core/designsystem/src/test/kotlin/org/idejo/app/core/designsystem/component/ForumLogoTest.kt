// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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

/** SH-01: a forum's logo sits on the surface around it, not on idejo's yellow. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ForumLogoTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `SH-01 a transparent logo shows the background it is placed on`() {
        val surface = Color(0xFF101010)
        rule.setContent {
            IdejoTheme {
                Box(Modifier.background(surface)) {
                    ForumLogo("Garden club", Modifier.testTag("logo"), image = {})
                }
            }
        }
        val pixels = rule.onNodeWithTag("logo").captureToImage().toPixelMap()
        assertEquals(surface, pixels[pixels.width / 2, pixels.height / 2])
    }
}
