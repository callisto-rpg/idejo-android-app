// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.privatemessages

import org.idejo.app.core.api.ApiProblem
import org.idejo.app.feature.settings.SignedInForum
import org.idejo.app.feature.settings.until
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** **Private messages** (PC-03) against the fake forum. */
class PrivateMessagesViewModelTest {
    @get:Rule val forum = SignedInForum()

    private fun loaded(): PrivateMessagesViewModel {
        forum.signIn()
        return PrivateMessagesViewModel(forum.sessions).also { vm ->
            vm.state.until { !it.loading }
        }
    }

    @Test
    fun `PC-03 the switch shows the member's choice and changes it`() {
        val vm = loaded()
        assertEquals(true, vm.state.value.acceptsPrivateMessages)

        vm.onEvent(PrivateMessagesEvent.Accept(false))

        assertEquals(false, vm.state.until { !it.saving }.acceptsPrivateMessages)
        val patch = forum.fake.requests.last { it.method == "PATCH" }
        assertEquals("""{"accepts_private_messages":false}""", patch.body?.utf8())
        assertEquals(false, forum.fake.choicesOf(forum.ada).acceptsPrivateMessages)
    }

    @Test
    fun `PC-03 a refused change puts the switch back with the server's words`() {
        val vm = loaded()
        forum.fake.failNext(422, "invalid_request", "Not now.")
        vm.onEvent(PrivateMessagesEvent.Accept(false))

        val failed = vm.state.until { !it.saving }
        assertEquals(true, failed.acceptsPrivateMessages)
        assertEquals("Not now.", (failed.problem as ApiProblem.Server).error.message)
    }
}
