// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.appsdevices

import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.data.Access
import org.idejo.app.feature.settings.SignedInForum
import org.idejo.app.feature.settings.eventually
import org.idejo.app.feature.settings.until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** **Apps and devices** (ST-05) against the fake forum. */
class AppsDevicesViewModelTest {
    @get:Rule val forum = SignedInForum()

    /** Ada on this device and on a tablet. */
    private fun loaded(): AppsDevicesViewModel {
        forum.fake.tokenFor(forum.ada, "Galaxy Tab")
        forum.signIn()
        return AppsDevicesViewModel(forum.sessions).also { vm -> vm.state.until { !it.loading } }
    }

    @Test
    fun `ST-05 the devices are listed, this one first and marked`() {
        val devices = requireNotNull(loaded().state.value.devices)
        assertEquals(2, devices.size)
        assertTrue(devices.first().current)
        assertEquals("Galaxy Tab", devices.last().name)
        assertFalse(devices.last().current)
    }

    @Test
    fun `ST-05 removing another device signs it out and drops its row`() {
        val vm = loaded()
        val tablet = requireNotNull(vm.state.value.devices).last()
        vm.onEvent(AppsDevicesEvent.Remove(tablet.id))

        val done = vm.state.until { it.removing == null && it.devices?.size == 1 }
        assertTrue(requireNotNull(done.devices).single().current)
        val sent = forum.fake.requests.last { it.method == "DELETE" }
        assertEquals("/api/v1/me/tokens/${tablet.id}", sent.url.encodedPath)
        assertEquals(Access.SignedIn, requireNotNull(forum.sessions.active).access.value)
    }

    @Test
    fun `ST-05 removing this device signs out of the forum`() {
        val vm = loaded()
        val origin = requireNotNull(forum.sessions.active).origin
        val here = requireNotNull(vm.state.value.devices).first()
        vm.onEvent(AppsDevicesEvent.Remove(here.id))

        eventually {
            forum.sessions.active
                ?.access
                ?.value == Access.Visitor
        }
        assertEquals(origin, forum.sessions.active?.origin)
        assertTrue(forum.fake.requests.any { it.url.encodedPath == "/api/v1/oauth/revoke" })
    }

    @Test
    fun `ST-05 a refusal keeps the row and shows the server message`() {
        val vm = loaded()
        val tablet = requireNotNull(vm.state.value.devices).last()
        forum.fake.failNext(422, "invalid_request", "That cannot be done now.")
        vm.onEvent(AppsDevicesEvent.Remove(tablet.id))

        val failed = vm.state.until { it.removing == null && it.problem != null }
        assertEquals(2, failed.devices?.size)
        assertEquals(
            "That cannot be done now.",
            (failed.problem as ApiProblem.Server).error.message,
        )
    }

    @Test
    fun `ST-05 offline, the row stays`() {
        val vm = loaded()
        val tablet = requireNotNull(vm.state.value.devices).last()
        forum.goOffline()
        vm.onEvent(AppsDevicesEvent.Remove(tablet.id))

        val failed = vm.state.until { it.removing == null && it.problem != null }
        assertEquals(2, failed.devices?.size)
        assertTrue(failed.problem is ApiProblem.Network)
    }

    @Test
    fun `ST-05 a list that cannot be read offers to try again`() {
        forum.signIn()
        forum.fake.failNext(500, "internal", "Something went wrong.")
        val vm = AppsDevicesViewModel(forum.sessions)
        vm.state.until { it.loadProblem != null }

        vm.onEvent(AppsDevicesEvent.Retry)

        assertEquals(
            1,
            vm.state
                .until { it.loadProblem == null && !it.loading }
                .devices
                ?.size,
        )
    }
}
