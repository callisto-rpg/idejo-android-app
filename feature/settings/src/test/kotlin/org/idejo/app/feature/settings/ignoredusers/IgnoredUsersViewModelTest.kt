// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.ignoredusers

import kotlinx.coroutines.runBlocking
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.testing.FakePeople
import org.idejo.app.feature.settings.SignedInForum
import org.idejo.app.feature.settings.until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** **Ignored users** (ST-08) against the fake forum. */
class IgnoredUsersViewModelTest {
    @get:Rule val forum = SignedInForum()

    private val leo = requireNotNull(FakePeople.Leo.id)
    private val mina = requireNotNull(FakePeople.Mina.id)

    /** Ada ignores [ids] from their cards, then opens the section. */
    private fun loaded(vararg ids: String): IgnoredUsersViewModel {
        val session = forum.signIn()
        runBlocking { ids.forEach { session.call { ignore(it, ignored = true) } } }
        return IgnoredUsersViewModel(forum.sessions).also { vm ->
            vm.state.until { !it.loading }
        }
    }

    @Test
    fun `ST-08 the section lists the members ignored`() {
        val state = loaded(leo, mina).state.value
        assertEquals(
            setOf("Leo Moreau", "Mina Kader"),
            state.members?.map { it.displayName }?.toSet(),
        )
        assertNull(state.loadProblem)
    }

    @Test
    fun `ST-08 nobody ignored is an empty list`() {
        assertEquals(emptyList<Any>(), loaded().state.value.members)
    }

    @Test
    fun `ST-08 stop ignoring takes the member off the list and off the server's`() {
        val vm = loaded(leo, mina)
        vm.onEvent(IgnoredUsersEvent.StopIgnoring(leo))

        val done = vm.state.until { it.saving == null && it.members?.size == 1 }
        assertEquals(listOf(mina), done.members?.map { it.id })
        val sent = forum.fake.requests.last { it.method == "DELETE" }
        assertEquals("/api/v1/users/$leo/ignore", sent.url.encodedPath)
        val again = IgnoredUsersViewModel(forum.sessions).state.until { !it.loading }
        assertEquals(listOf(mina), again.members?.map { it.id })
    }

    @Test
    fun `ST-08 a refusal keeps the row and shows the server message`() {
        val vm = loaded(leo)
        forum.fake.failNext(422, "invalid_request", "That cannot be done now.")
        vm.onEvent(IgnoredUsersEvent.StopIgnoring(leo))

        val failed = vm.state.until { it.saving == null && it.problem != null }
        assertEquals(listOf(leo), failed.members?.map { it.id })
        val problem = failed.problem as ApiProblem.Server
        assertEquals("That cannot be done now.", problem.error.message)
    }

    @Test
    fun `ST-08 offline, the row stays`() {
        val vm = loaded(leo)
        forum.goOffline()
        vm.onEvent(IgnoredUsersEvent.StopIgnoring(leo))

        val failed = vm.state.until { it.saving == null && it.problem != null }
        assertEquals(listOf(leo), failed.members?.map { it.id })
        assertTrue(failed.problem is ApiProblem.Network)
    }

    @Test
    fun `ST-08 a list that cannot be read offers to try again`() {
        forum.signIn()
        forum.fake.failNext(500, "internal", "Something went wrong.")
        val vm = IgnoredUsersViewModel(forum.sessions)
        vm.state.until { it.loadProblem != null }

        vm.onEvent(IgnoredUsersEvent.Retry)

        val read = vm.state.until { it.loadProblem == null && !it.loading }
        assertEquals(emptyList<Any>(), read.members)
    }
}
