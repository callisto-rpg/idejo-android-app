// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The sidebar opened and closed by gestures and back (SH-30), docked when wide (SH-60). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class SidebarFrameTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var open by mutableStateOf(false)

    private fun frame(
        wide: Boolean = false,
        fold: Fold? = null,
    ) = rule.setContent {
        SidebarFrame(
            wide = wide,
            fold = fold,
            open = open,
            onOpen = { open = true },
            onClose = { open = false },
            topBar = {},
            sidebar = { Text("Categories") },
        ) { Text("The page") }
    }

    @Test
    fun `SH-30 a swipe to the right across the page opens the sidebar, to the left closes it`() {
        frame()
        rule.onNodeWithTag(OVER).assertDoesNotExist()

        rule
            .onNodeWithTag(
                PAGE,
            ).performTouchInput { swipeRight(startX = 120f, endX = right - 120f) }
        rule.onNodeWithTag(OVER).assertExists()

        rule.onNodeWithTag(OVER).performTouchInput { swipeLeft(startX = right - 120f, endX = 120f) }
        rule.waitForIdle()
        rule.onNodeWithTag(OVER).assertDoesNotExist()
    }

    @Test
    fun `SH-30 a swipe from the screen's edge is system back's, not the sidebar's`() {
        frame()
        rule.onNodeWithTag(PAGE).performTouchInput { swipeRight(startX = 2f, endX = centerX) }
        rule.onNodeWithTag(OVER).assertDoesNotExist()
    }

    @Test
    fun `SH-30 back closes the sidebar`() {
        frame()
        open = true
        rule.onNodeWithTag(OVER).assertExists()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag(OVER).assertDoesNotExist()
    }

    @Test
    fun `SH-60 an expanded window keeps the sidebar beside the page`() {
        frame(wide = true)
        rule.onNodeWithTag(DOCKED).assertExists()
        rule.onNodeWithText("Categories").assertExists()
        rule.onNodeWithText("The page").assertExists()
        // A swipe does not open it over the page: it is already there.
        rule.onNodeWithText("The page").performTouchInput { swipeRight() }
        rule.onNodeWithTag(OVER).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w800dp-h600dp-mdpi")
    fun `SH-61 in book posture the sidebar keeps left of the hinge, the page right of it`() {
        frame(fold = Fold(vertical = true, start = 390.dp, end = 410.dp))
        val sidebar = rule.onNodeWithTag(DOCKED).getUnclippedBoundsInRoot()
        val page = rule.onNodeWithText("The page").getUnclippedBoundsInRoot()
        assertTrue("sidebar ends at the hinge: ${sidebar.right}", sidebar.right <= 390.dp)
        assertTrue("page starts past the hinge: ${page.left}", page.left >= 410.dp)
    }

    @Test
    @Config(qualifiers = "w600dp-h800dp-mdpi")
    fun `SH-61 in tabletop posture the page keeps above the hinge`() {
        frame(fold = Fold(vertical = false, start = 390.dp, end = 410.dp))
        val page = rule.onNodeWithText("The page").getUnclippedBoundsInRoot()
        val sidebar = rule.onNodeWithTag(DOCKED).getUnclippedBoundsInRoot()
        assertTrue("page ends above the hinge: ${page.bottom}", page.bottom <= 390.dp)
        assertTrue("sidebar starts below it: ${sidebar.top}", sidebar.top >= 410.dp)
    }
}
