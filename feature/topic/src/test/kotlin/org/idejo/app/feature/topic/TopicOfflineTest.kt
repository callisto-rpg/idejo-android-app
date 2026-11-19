// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.idejo.app.core.model.Topic
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Nothing queued offline (OF-05): the topic's **Follow** waits for the forum. */
@RunWith(RobolectricTestRunner::class)
class TopicOfflineTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `OF-05 offline, Follow is disabled`() {
        rule.setContent { Follow(Topic("t_1", "Tomatoes"), TopicActions(), offline = true) }
        rule.onNodeWithText("Follow").assertIsNotEnabled()
    }

    @Test
    fun `OF-05 online, Follow works`() {
        rule.setContent { Follow(Topic("t_1", "Tomatoes"), TopicActions(), offline = false) }
        rule.onNodeWithText("Follow").assertIsEnabled()
    }
}
