// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.idejo.app.core.data.SidebarCategory
import org.idejo.app.core.data.SidebarSection
import org.idejo.app.core.data.SidebarState
import org.idejo.app.core.model.Category
import org.idejo.app.core.model.Topic
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Nothing queued offline (OF-05): the sidebar's follows wait for the forum. */
@RunWith(RobolectricTestRunner::class)
class SidebarOfflineTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val actions =
        object : SidebarActions {
            override fun open(place: Place) = Unit

            override fun fold(categoryId: String) = Unit

            override fun rollLinks(rolled: Boolean) = Unit

            override fun follow(
                topic: Topic,
                followed: Boolean,
            ) = Unit

            override fun unfollowAndMute(topic: Topic) = Unit

            override fun markUnread(topic: Topic) = Unit

            override fun copyLink(topic: Topic) = Unit

            override fun share(topic: Topic) = Unit

            override fun retry() = Unit
        }

    private fun show(offline: Boolean) =
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
                    offline = offline,
                ),
                emptySet(),
                false,
                signedIn = true,
                openTopic = null,
                actions = actions,
            )
        }

    @Test
    fun `OF-05 offline, a followed topic's Unfollow is disabled`() {
        show(offline = true)
        rule.onNodeWithContentDescription("Unfollow Tomatoes").assertIsNotEnabled()
    }

    @Test
    fun `OF-05 online, it works`() {
        show(offline = false)
        rule.onNodeWithContentDescription("Unfollow Tomatoes").assertIsEnabled()
    }
}
