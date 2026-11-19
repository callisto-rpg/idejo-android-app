// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.deleteaccount

import org.idejo.app.core.data.InboxShortcuts
import org.idejo.app.feature.settings.ProfileAndAccountViewModel
import org.idejo.app.feature.settings.SignedInForum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** **Delete account** (ST-06): the forum's deletion page, from its discovery. */
class DeleteAccountViewModelTest {
    @get:Rule val forum = SignedInForum()

    @Test
    fun `ST-06 the section opens the forum's deletion page`() {
        forum.signIn()
        val url = DeleteAccountViewModel(forum.sessions).state.value.deletionUrl
        assertEquals(
            forum.fake.address
                .resolve("/users/settings?tab=account#delete-heading")
                ?.toString(),
            url,
        )
    }

    @Test
    fun `ST-06 a forum without a deletion page has no Delete account section`() {
        forum.fake.accountDeletion = false
        forum.signIn()
        assertNull(DeleteAccountViewModel(forum.sessions).state.value.deletionUrl)
        val keys =
            ProfileAndAccountViewModel(
                forum.sessions,
                forum.group,
                InboxShortcuts(),
            ).sections.value.map {
                it.key
            }
        assertFalse("delete-account" in keys)
    }

    @Test
    fun `ST-06 a forum with one lists the section under Account`() {
        forum.signIn()
        val keys =
            ProfileAndAccountViewModel(
                forum.sessions,
                forum.group,
                InboxShortcuts(),
            ).sections.value.map {
                it.key
            }
        assertTrue("delete-account" in keys)
    }

    @Test
    fun `ST-06 a path is the forum's own page`() {
        assertEquals(
            "https://garden.example.org/users/settings?tab=account",
            deletionPage("https://garden.example.org/", "/users/settings?tab=account"),
        )
        assertEquals(
            "https://garden.example.org/a",
            deletionPage("https://garden.example.org", "https://garden.example.org/a"),
        )
        assertNull(deletionPage("https://garden.example.org", "not a url at all\u0000"))
    }
}
