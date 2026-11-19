// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.parts

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.idejo.app.core.model.EverywhereResult
import org.idejo.app.feature.settings.SignedInForum
import org.idejo.app.feature.settings.until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** **Save for all forums** on the inbox's Profile and account (ST-07, IX-17). */
@RunWith(RobolectricTestRunner::class)
class EverywhereTest {
    @get:Rule val forum = SignedInForum()

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `ST-07 the inbox saves a section on every forum`() {
        forum.inbox.everywhere = EverywhereResult(saved = listOf("Garden club", "Bee keepers"))
        forum.onInbox()
        val model = EverywhereViewModel(forum.sessions)
        model.state.until { it.offered }

        model.save(listOf("writing"))
        val done = model.state.until { it.outcome != null }
        assertEquals(EverywhereOutcome.Saved(2), done.outcome)
        assertEquals(listOf(listOf("writing")), forum.inbox.savedEverywhere.toList())
    }

    @Test
    fun `ST-07 the forums missed are named, and an unreachable group said so`() {
        forum.inbox.everywhere = EverywhereResult(listOf("Garden club"), listOf("Bee keepers"))
        forum.onInbox()
        val model = EverywhereViewModel(forum.sessions)
        model.state.until { it.offered }

        model.save(listOf("email"))
        assertEquals(
            EverywhereOutcome.Missed(listOf("Bee keepers")),
            model.state.until { it.outcome != null }.outcome,
        )

        forum.inbox.failNext(503, "unavailable", "Your forums could not be reached.")
        model.save(listOf("email"))
        assertEquals(
            EverywhereOutcome.Unreachable,
            model.state.until { !it.saving && it.outcome != null }.outcome,
        )
    }

    @Test
    fun `ST-07 not offered on a forum, nor by an inbox without it`() {
        forum.onInbox()
        assertFalse(EverywhereViewModel(forum.sessions).state.value.offered)

        forum.signIn()
        assertFalse(EverywhereViewModel(forum.sessions).state.value.offered)
    }

    @Test
    fun `ST-07 the button and what it did`() {
        var asked = 0
        rule.setContent {
            SaveForAllForums(
                EverywhereUiState(
                    offered = true,
                    outcome = EverywhereOutcome.Missed(listOf("Bees")),
                ),
                onSave = { asked++ },
            )
        }
        rule.onNodeWithText("Save for all forums").performClick()
        assertEquals(1, asked)
        rule
            .onNodeWithText(
                "Not saved on Bees: they did not answer or need an update. Try again later.",
            ).assertExists()
    }
}
