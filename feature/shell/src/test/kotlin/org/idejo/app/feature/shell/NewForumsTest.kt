// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.idejo.app.core.designsystem.component.Crumb
import org.idejo.app.core.designsystem.component.TopBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A forum joined since last seen (SI-07): the logo's dot, **Your forums** (MD-02). */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
class NewForumsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `SI-07 the logo says a joined forum waits to be connected`() {
        rule.setContent { TopBar(listOf(Crumb.Forum("Garden club", {}, dot = true))) }
        rule
            .onNodeWithContentDescription(
                "Garden club menu, a forum you joined is waiting to be connected",
            ).assertExists()
    }

    @Test
    fun `SI-07 without a new forum, the logo is the menu only`() {
        rule.setContent { TopBar(listOf(Crumb.Forum("Garden club", {}))) }
        rule.onNodeWithContentDescription("Garden club menu").assertExists()
    }

    @Test
    fun `MD-02 Your forums leads to the inbox, with the dot`() {
        var opened = 0
        rule.setContent {
            LogoMenu(
                "Garden club",
                "garden.example.org",
                {},
                {},
                {},
                onForums = { opened++ },
                newForums = true,
            )
        }
        rule
            .onNodeWithContentDescription(
                "A forum you joined is waiting to be connected",
            ).assertExists()
        rule.onNodeWithText("Your forums").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `MD-03 single-forum mode keeps its note`() {
        rule.setContent { LogoMenu("Garden club", "garden.example.org", {}, {}, {}) }
        rule.onNodeWithText("Add a forum").assertExists()
    }
}
