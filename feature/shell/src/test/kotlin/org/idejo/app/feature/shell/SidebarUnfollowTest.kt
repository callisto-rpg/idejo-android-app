// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import org.idejo.app.core.data.SidebarCategory
import org.idejo.app.core.data.SidebarSection
import org.idejo.app.core.data.SidebarState
import org.idejo.app.core.model.Category
import org.idejo.app.core.model.Topic
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** SH-33: the ×'s notice, "Topic unfollowed. Tap to cancel", for 3 s before it is sent. */
@RunWith(RobolectricTestRunner::class)
class SidebarUnfollowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val sent = mutableListOf<Pair<String, Boolean>>()

    private val actions =
        object : SidebarActions {
            override fun open(place: Place) = Unit

            override fun fold(categoryId: String) = Unit

            override fun rollLinks(rolled: Boolean) = Unit

            override fun follow(
                topic: Topic,
                followed: Boolean,
            ) {
                sent += topic.id to followed
            }

            override fun unfollowAndMute(topic: Topic) = Unit

            override fun markUnread(topic: Topic) = Unit

            override fun copyLink(topic: Topic) = Unit

            override fun share(topic: Topic) = Unit

            override fun retry() = Unit
        }

    private fun show() {
        rule.setContent {
            Sidebar(
                SidebarState(
                    sections =
                        listOf(
                            SidebarSection(
                                null,
                                listOf(
                                    SidebarCategory(
                                        Category("c_1", "Vegetables", "vegetables"),
                                        listOf(Topic("t_1", "Tomatoes")),
                                    ),
                                ),
                            ),
                        ),
                    loading = false,
                ),
                emptySet(),
                false,
                signedIn = true,
                openTopic = null,
                actions = actions,
            )
        }
    }

    @Test
    fun `SH-33 the x shows the notice, then unfollows once it has faded`() {
        show()
        rule
            .onNodeWithContentDescription("Unfollow Tomatoes", useUnmergedTree = true)
            .onParent()
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithText("Topic unfollowed. Tap to cancel").assertExists()
        assertEquals(emptyList<Pair<String, Boolean>>(), sent)
        rule.mainClock.advanceTimeBy(3_500)
        assertEquals(listOf("t_1" to false), sent)
        rule.onNodeWithText("Tomatoes").assertDoesNotExist()
    }

    @Test
    fun `SH-33 a tap on the notice cancels, and nothing is sent`() {
        show()
        rule
            .onNodeWithContentDescription("Unfollow Tomatoes", useUnmergedTree = true)
            .onParent()
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithText("Topic unfollowed. Tap to cancel").performClick()
        rule.mainClock.advanceTimeBy(4_000)
        assertEquals(emptyList<Pair<String, Boolean>>(), sent)
        rule.onNodeWithText("Tomatoes").assertExists()
    }
}
