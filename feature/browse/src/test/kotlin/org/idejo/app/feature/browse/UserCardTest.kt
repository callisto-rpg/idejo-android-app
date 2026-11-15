// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.idejo.app.core.model.UserCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What a member does from someone's user card. */
@RunWith(RobolectricTestRunner::class)
class UserCardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val leo = UserCard("u_leo", "Leo Moreau", ignoredByMe = false)

    @Test
    fun `MA-06 Ignore from the user card`() {
        val asked = mutableListOf<Boolean>()
        rule.setContent {
            UserCardScreen(UserCardUi(leo, loading = false), { asked += it }, {}, {})
        }
        rule.onNodeWithText("Ignore").performClick()
        assertEquals(listOf(true), asked)
    }
}
