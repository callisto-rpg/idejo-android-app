// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.password

import kotlinx.coroutines.runBlocking
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.ForumApi
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.data.Access
import org.idejo.app.core.testing.FakePeople
import org.idejo.app.feature.settings.SignedInForum
import org.idejo.app.feature.settings.eventually
import org.idejo.app.feature.settings.until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** **Change password** (ST-04) against the fake forum. */
class PasswordViewModelTest {
    @get:Rule val forum = SignedInForum()

    private fun typed(
        current: String = FakePeople.PASSWORD,
        new: String = "a much longer secret",
        repeat: String = new,
    ): PasswordViewModel {
        forum.signIn()
        return PasswordViewModel(forum.sessions).apply {
            onEvent(PasswordEvent.EditCurrent(current))
            onEvent(PasswordEvent.EditNew(new))
            onEvent(PasswordEvent.EditRepeat(repeat))
        }
    }

    @Test
    fun `ST-04 the change is sent, and every device signs in again, this one too`() {
        val other = forum.fake.tokenFor(forum.ada, "Galaxy Tab")
        val vm = typed()
        vm.onEvent(PasswordEvent.Change)

        val done = vm.state.until { it.changed }
        assertEquals("", done.current)
        assertEquals("", done.new)
        val sent = forum.fake.requests.last { it.method == "PUT" }
        assertEquals("/api/v1/me/password", sent.url.encodedPath)
        assertEquals(
            """{"current_password":"correct horse battery","password":"a much longer secret",""" +
                """"password_confirmation":"a much longer secret"}""",
            sent.body?.utf8(),
        )
        // This device: "You were signed out" (SI-09), then Sign in.
        eventually {
            forum.sessions.active
                ?.access
                ?.value == Access.SignedOut
        }
        // The other device too: its token is refused.
        val tablet = ForumApi(forum.fake.address, "/api/v1", { other }, IdejoHttp.forLocalTesting())
        val refused = runBlocking { tablet.me() } as ApiResult.Failure
        assertTrue((refused.problem as ApiProblem.Server).signedOut)
    }

    @Test
    fun `ST-04 a wrong current password keeps what was typed and says so`() {
        val vm = typed(current = "not my password")
        vm.onEvent(PasswordEvent.Change)

        val failed = vm.state.until { !it.saving && it.problem != null }
        assertFalse(failed.changed)
        assertEquals("not my password", failed.current)
        assertEquals(
            "That is not your current password.",
            (failed.problem as ApiProblem.Server).error.message,
        )
        assertEquals(
            Access.SignedIn,
            forum.sessions.active
                ?.access
                ?.value,
        )
    }

    @Test
    fun `ST-04 the server's rules, not the app's, refuse a short password`() {
        val vm = typed(new = "short")
        vm.onEvent(PasswordEvent.Change)

        val failed = vm.state.until { !it.saving && it.problem != null }
        assertEquals(
            "New password should be at least 12 characters.",
            (failed.problem as ApiProblem.Server).error.message,
        )
    }

    @Test
    fun `ST-04 a forum without passwords says so`() {
        forum.fake.passwords = false
        val vm = typed()
        vm.onEvent(PasswordEvent.Change)

        val failed = vm.state.until { !it.saving && it.problem != null }
        assertTrue(
            (failed.problem as ApiProblem.Server).error.message.startsWith(
                "Passwords are not used",
            ),
        )
    }

    @Test
    fun `ST-04 offline, nothing typed is lost`() {
        val vm = typed()
        forum.goOffline()
        vm.onEvent(PasswordEvent.Change)

        val failed = vm.state.until { !it.saving && it.problem != null }
        assertTrue(failed.problem is ApiProblem.Network)
        assertEquals(FakePeople.PASSWORD, failed.current)
    }

    @Test
    fun `ST-04 the passwords never show in the state's text`() {
        val vm = typed()
        assertFalse(
            vm.state.value
                .toString()
                .contains(FakePeople.PASSWORD),
        )
    }
}
