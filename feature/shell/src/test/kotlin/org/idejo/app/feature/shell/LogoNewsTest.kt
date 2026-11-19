// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.idejo.app.core.designsystem.component.Crumb
import org.idejo.app.core.designsystem.component.LogoNews
import org.idejo.app.core.designsystem.component.TopBar
import org.idejo.app.core.model.MentionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The logo's dot for the member's other forums (SH-03), as the web's community dot. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
class LogoNewsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val garden = "https://garden.example.org/"
    private val bees = "https://bees.example.org/"

    @Test
    fun `SH-03 white for new messages elsewhere, red when one mentions the member`() {
        assertEquals(LogoNews.NewMessages, logoNews(mapOf(bees to MentionState.Reply), garden))
        assertEquals(LogoNews.Mention, logoNews(mapOf(bees to MentionState.Mention), garden))
        // A +role mention counts as the web's `mentions` do.
        assertEquals(LogoNews.Mention, logoNews(mapOf(bees to MentionState.Role), garden))
    }

    @Test
    fun `SH-03 the forum open is left out`() {
        assertNull(logoNews(mapOf(garden to MentionState.Mention), garden))
        assertNull(logoNews(mapOf(bees to MentionState.None), garden))
    }

    @Test
    fun `SH-03 the logo says why it has a dot`() {
        rule.setContent {
            TopBar(listOf(Crumb.Forum("Garden club", {}, news = LogoNews.Mention)))
        }
        rule
            .onNodeWithContentDescription(
                "Garden club menu, you were mentioned in another of your forums",
            ).assertExists()
    }

    @Test
    fun `SH-03 new messages elsewhere, said on the logo`() {
        rule.setContent {
            TopBar(listOf(Crumb.Forum("Garden club", {}, news = LogoNews.NewMessages)))
        }
        rule
            .onNodeWithContentDescription("Garden club menu, new messages in your other forums")
            .assertExists()
    }
}
