// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.messagecontrols

import org.idejo.app.core.api.ApiProblem
import org.idejo.app.feature.settings.SignedInForum
import org.idejo.app.feature.settings.until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** **Message controls** (ST-03, MA-01) against the fake forum. */
class MessageControlsViewModelTest {
    @get:Rule val forum = SignedInForum()

    private fun loaded(): MessageControlsViewModel {
        forum.signIn()
        return MessageControlsViewModel(forum.sessions).also { vm ->
            vm.state.until { !it.loading }
        }
    }

    @Test
    fun `ST-03 the section shows the member's choices`() {
        val state = loaded().state.value
        assertEquals(false, state.onMobile)
        assertEquals(false, state.onDesktop)
        assertNull(state.loadProblem)
    }

    @Test
    fun `ST-03 turning on controls on mobile patches only that field`() {
        val vm = loaded()
        vm.onEvent(MessageControlsEvent.OnMobile(true))

        assertEquals(true, vm.state.value.onMobile)
        val saved = vm.state.until { !it.saving }
        assertEquals(true, saved.onMobile)
        val patch = forum.fake.requests.last { it.method == "PATCH" }
        assertEquals("""{"controls_on_mobile":true}""", patch.body?.utf8())
        assertEquals(true, forum.fake.choicesOf(forum.ada).controlsOnMobile)
        assertEquals(false, forum.fake.choicesOf(forum.ada).controlsOnDesktop)
    }

    @Test
    fun `ST-03 a refused patch reverts the switch and shows the server message`() {
        val vm = loaded()
        forum.fake.failNext(422, "invalid_request", "That choice cannot be saved now.")
        vm.onEvent(MessageControlsEvent.OnDesktop(true))

        val failed = vm.state.until { !it.saving }
        assertEquals(false, failed.onDesktop)
        val problem = failed.problem as ApiProblem.Server
        assertEquals("That choice cannot be saved now.", problem.error.message)
        assertEquals(false, forum.fake.choicesOf(forum.ada).controlsOnDesktop)
    }

    @Test
    fun `ST-03 offline, the switch goes back and nothing was changed`() {
        val vm = loaded()
        forum.goOffline()
        vm.onEvent(MessageControlsEvent.OnMobile(true))

        val failed = vm.state.until { !it.saving }
        assertEquals(false, failed.onMobile)
        assertTrue(failed.problem is ApiProblem.Network)
    }

    @Test
    fun `MA-01 the choice reaches the open topic at once`() {
        val vm = loaded()
        // What the topic page's message controls read.
        val open = requireNotNull(forum.sessions.active).me.state
        vm.onEvent(MessageControlsEvent.OnMobile(true))

        assertEquals(true, open.until { it?.controlsOnMobile == true }?.controlsOnMobile)
    }

    @Test
    fun `ST-03 a server without the choices hides the rows`() {
        forum.fake.memberChoices = false
        val state = loaded().state.value
        assertNull(state.onMobile)
        assertNull(state.onDesktop)
    }

    @Test
    fun `ST-03 a section that cannot be read offers to try again`() {
        forum.signIn()
        forum.fake.failNext(500, "internal", "Something went wrong.")
        val vm = MessageControlsViewModel(forum.sessions)
        vm.state.until { it.loadProblem != null }

        vm.onEvent(MessageControlsEvent.Retry)

        val read = vm.state.until { it.loadProblem == null && !it.loading }
        assertEquals(false, read.onMobile)
    }
}
