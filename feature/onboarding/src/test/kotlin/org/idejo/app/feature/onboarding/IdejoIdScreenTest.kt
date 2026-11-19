// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.idejo.app.core.data.SignInState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** **Sign in with idejo ID** (SI-06, MD-02). */
@RunWith(RobolectricTestRunner::class)
class IdejoIdScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `MD-01 the welcome screen offers idejo ID and a forum address`() {
        val chosen = mutableListOf<String>()
        rule.setContent {
            WelcomeScreen(onIdejoId = { chosen += "id" }, onEnterAddress = { chosen += "address" })
        }
        rule.onNodeWithText("Sign in with idejo ID").performClick()
        rule.onNodeWithText("Enter a forum address").performClick()
        assertEquals(listOf("id", "address"), chosen)
    }

    @Test
    fun `SI-06 signs in to my idejo org, or to another group's inbox`() {
        val inboxes = mutableListOf<String>()
        rule.setContent {
            IdejoIdScreen(AddForumState.Typing(), SignInState.Idle, { inboxes += it }, {})
        }
        rule.onNodeWithText("Sign in with idejo ID").performClick()
        rule.onNodeWithText("Use another idejo group").performClick()
        rule.onNodeWithText("The group's inbox").performTextInput("my.example.org")
        rule.onNodeWithText("Sign in with idejo ID").performClick()
        assertEquals(listOf("my.idejo.org", "my.example.org"), inboxes)
    }
}
