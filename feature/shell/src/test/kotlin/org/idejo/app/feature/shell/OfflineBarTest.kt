// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The saved copy on screen while the forum cannot be reached (OF-02). */
@RunWith(RobolectricTestRunner::class)
class OfflineBarTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `OF-02 the bar says the page is a saved copy, and why actions wait`() {
        rule.setContent { OfflineBar(System.currentTimeMillis()) }
        rule
            .onNodeWithText("Offline — showing saved copy from", substring = true)
            .assertExists()
        rule
            .onNodeWithText("What needs the forum waits until it answers.", substring = true)
            .assertExists()
    }

    @Test
    fun `OF-02 no bar while the forum answers`() {
        rule.setContent { OfflineBar(null) }
        rule.onAllNodesWithText("Offline", substring = true).assertCountEquals(0)
    }
}
