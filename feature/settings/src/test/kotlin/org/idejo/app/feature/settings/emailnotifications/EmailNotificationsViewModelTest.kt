// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.emailnotifications

import kotlinx.coroutines.runBlocking
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.feature.settings.SignedInForum
import org.idejo.app.feature.settings.until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** **Email notifications** (ST-03) against the fake forum. */
class EmailNotificationsViewModelTest {
    @get:Rule val forum = SignedInForum()

    private fun loaded(): EmailNotificationsViewModel {
        forum.signIn()
        return EmailNotificationsViewModel(forum.sessions).also { vm ->
            vm.state.until { !it.loading }
        }
    }

    @Test
    fun `ST-03 the section shows idejo's choices and the member's username`() {
        val vm = loaded()
        // What the shell does on opening the forum.
        runBlocking { requireNotNull(forum.sessions.active).me.refresh() }
        val state = vm.state.until { it.username != null }
        val email = requireNotNull(state.email)
        assertEquals(true, email.mentions)
        assertEquals(false, email.roleMentions)
        assertEquals(true, email.ownTopics)
        assertEquals(false, email.followedTopics)
        assertEquals(false, email.newTopics)
        assertEquals(false, email.allActivity)
        assertEquals(true, email.onceAWeek)
        assertEquals("ada", state.username)
        assertNull(state.loadProblem)
    }

    @Test
    fun `ST-03 a switch patches only its choice`() {
        val vm = loaded()
        vm.onEvent(EmailNotificationsEvent.Set(EmailChoice.FollowedTopics, true))

        assertEquals(
            true,
            vm.state.value.email
                ?.followedTopics,
        )
        val saved = vm.state.until { !it.saving }
        assertEquals(true, saved.email?.followedTopics)
        val patch = forum.fake.requests.last { it.method == "PATCH" }
        assertEquals("""{"email":{"followed_topics":true}}""", patch.body?.utf8())
        val server = requireNotNull(forum.fake.notificationSettingsOf(forum.ada).email)
        assertEquals(true, server.followedTopics)
        assertEquals(true, server.mentions)
        assertEquals(
            true,
            forum.fake
                .notificationSettingsOf(forum.ada)
                .push
                ?.reply,
        )
    }

    @Test
    fun `ST-03 the weekly choice is its own switch`() {
        val vm = loaded()
        vm.onEvent(EmailNotificationsEvent.Set(EmailChoice.OnceAWeek, false))

        assertEquals(
            false,
            vm.state
                .until { !it.saving }
                .email
                ?.onceAWeek,
        )
        val patch = forum.fake.requests.last { it.method == "PATCH" }
        assertEquals("""{"email":{"once_a_week":false}}""", patch.body?.utf8())
    }

    @Test
    fun `ST-03 a refused patch reverts the switch and shows the server message`() {
        val vm = loaded()
        forum.fake.failNext(422, "validation", "That choice cannot be saved now.")
        vm.onEvent(EmailNotificationsEvent.Set(EmailChoice.Mentions, false))

        val failed = vm.state.until { !it.saving }
        assertEquals(true, failed.email?.mentions)
        val problem = failed.problem as ApiProblem.Server
        assertEquals("That choice cannot be saved now.", problem.error.message)
        assertEquals(
            true,
            forum.fake
                .notificationSettingsOf(forum.ada)
                .email
                ?.mentions,
        )
    }

    @Test
    fun `ST-03 offline, the switch goes back and nothing was changed`() {
        val vm = loaded()
        forum.goOffline()
        vm.onEvent(EmailNotificationsEvent.Set(EmailChoice.AllActivity, true))

        val failed = vm.state.until { !it.saving }
        assertEquals(false, failed.email?.allActivity)
        assertTrue(failed.problem is ApiProblem.Network)
    }

    @Test
    fun `ST-03 a section that cannot be read offers to try again`() {
        forum.signIn()
        forum.fake.failNext(500, "internal", "Something went wrong.")
        val vm = EmailNotificationsViewModel(forum.sessions)
        vm.state.until { it.loadProblem != null }

        vm.onEvent(EmailNotificationsEvent.Retry)

        val read = vm.state.until { it.loadProblem == null && !it.loading }
        assertEquals(true, read.email?.mentions)
    }
}
