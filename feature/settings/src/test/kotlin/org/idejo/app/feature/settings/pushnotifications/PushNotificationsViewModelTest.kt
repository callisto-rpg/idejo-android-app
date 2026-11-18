// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.pushnotifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.data.DistributorChoice
import org.idejo.app.core.data.ForumSession
import org.idejo.app.core.data.PushDistributor
import org.idejo.app.core.data.PushRegistrar
import org.idejo.app.core.data.PushStatus
import org.idejo.app.core.testing.FakePeople
import org.idejo.app.feature.settings.SignedInForum
import org.idejo.app.feature.settings.until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** **Push notifications** (NT-08) against the fake forum. */
class PushNotificationsViewModelTest {
    @get:Rule val forum = SignedInForum()

    private lateinit var session: ForumSession

    /** The connector as the tests play it: [answer] is what the device has. */
    private class Distributor(
        val answer: DistributorChoice,
    ) : PushDistributor {
        override fun choice() = answer

        override fun register(
            instance: String,
            name: String,
            vapid: String?,
        ) = Unit

        override fun unregister(instance: String) = Unit
    }

    private var distributor = Distributor(DistributorChoice.Ready)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Not started: only the NT-06 test needs this device's own registration. */
    private val registrar by lazy { PushRegistrar(forum.sessions, distributor, "Pixel 8", scope) }

    @After
    fun stop() = scope.cancel()

    private fun loaded(devices: Boolean = true): PushNotificationsViewModel {
        session = forum.signIn()
        if (devices) {
            forum.fake.pushRegistration(FakePeople.Ada, "Pixel 8")
            forum.fake.pushRegistration(FakePeople.Ada, "Firefox on Linux")
        }
        return PushNotificationsViewModel(forum.sessions, registrar).also { vm ->
            vm.state.until { !it.loading }
        }
    }

    @Test
    fun `NT-08 the section shows the five types and the devices`() {
        val state = loaded().state.value
        val push = requireNotNull(state.push)
        assertEquals(true, push.reply)
        assertEquals(true, push.mention)
        assertEquals(true, push.roleMention)
        assertEquals(true, push.followed)
        assertEquals(true, push.privateMessages)
        assertEquals(listOf("Pixel 8", "Firefox on Linux"), state.devices?.map { it.name })
        // No registration of this app's token yet (NT-02).
        assertFalse(state.thisDeviceOn)
    }

    @Test
    fun `NT-08 a switch patches only its type`() {
        val vm = loaded()
        vm.onEvent(PushNotificationsEvent.Set(PushChoice.Followed, false))

        assertEquals(
            false,
            vm.state.value.push
                ?.followed,
        )
        assertEquals(
            false,
            vm.state
                .until { !it.saving }
                .push
                ?.followed,
        )
        val patch = forum.fake.requests.last { it.method == "PATCH" }
        assertEquals("""{"push":{"followed":false}}""", patch.body?.utf8())
        val server = forum.fake.notificationSettingsOf(forum.ada)
        assertEquals(false, server.push?.followed)
        assertEquals(true, server.email?.mentions)
    }

    @Test
    fun `NT-08 a refused patch reverts the switch with the server message`() {
        val vm = loaded()
        forum.fake.failNext(422, "validation", "That choice cannot be saved now.")
        vm.onEvent(PushNotificationsEvent.Set(PushChoice.PrivateMessages, false))

        val failed = vm.state.until { !it.saving }
        assertEquals(true, failed.push?.privateMessages)
        assertEquals(
            "That choice cannot be saved now.",
            (failed.problem as ApiProblem.Server).error.message,
        )
    }

    @Test
    fun `NT-08 offline, the switch goes back`() {
        val vm = loaded()
        forum.goOffline()
        vm.onEvent(PushNotificationsEvent.Set(PushChoice.Reply, false))

        val failed = vm.state.until { !it.saving }
        assertEquals(true, failed.push?.reply)
        assertTrue(failed.problem is ApiProblem.Network)
    }

    @Test
    fun `NT-08 removing a device ends its registration`() {
        val vm = loaded()
        val laptop = requireNotNull(vm.state.value.devices).last()
        vm.onEvent(PushNotificationsEvent.Remove(laptop.id))

        val done = vm.state.until { it.removing == null && it.devices?.size == 1 }
        assertEquals(listOf("Pixel 8"), done.devices?.map { it.name })
        val sent = forum.fake.requests.last { it.method == "DELETE" }
        assertEquals("/api/v1/me/push/unifiedpush/${laptop.id}", sent.url.encodedPath)
    }

    @Test
    fun `NT-08 this device is marked when its token registered`() {
        session = forum.signIn()
        val token = forum.fake.tokenFor(forum.ada)
        forum.fake.pushRegistration(FakePeople.Ada, "Other", token)
        val state =
            PushNotificationsViewModel(
                forum.sessions,
                registrar,
            ).state.until { !it.loading }
        // Registered by another token: not this device.
        assertFalse(state.thisDeviceOn)
    }

    @Test
    fun `NT-08 a test notification goes to the devices`() {
        val vm = loaded()
        vm.onEvent(PushNotificationsEvent.SendTest)
        assertEquals(TestResult.Sent, vm.state.until { !it.testing && it.tested != null }.tested)
        assertTrue(forum.fake.requests.any { it.url.encodedPath == "/api/v1/me/push/test" })
    }

    @Test
    fun `NT-08 a test with no device says to turn them on first`() {
        val vm = loaded(devices = false)
        registrar.start()
        assertEquals(emptyList<Any>(), vm.state.value.devices)
        vm.onEvent(PushNotificationsEvent.SendTest)
        assertEquals(
            TestResult.NoDevice,
            vm.state.until { !it.testing && it.tested != null }.tested,
        )
    }

    @Test
    fun `NT-08 a refused test shows the server message`() {
        val vm = loaded()
        forum.fake.failNext(429, "rate_limited", "Too many tests. Try again in a minute.")
        vm.onEvent(PushNotificationsEvent.SendTest)

        val failed = vm.state.until { !it.testing && it.problem != null }
        assertEquals(
            "Too many tests. Try again in a minute.",
            (failed.problem as ApiProblem.Server).error.message,
        )
    }

    @Test
    fun `NT-08 a section that cannot be read offers to try again`() {
        forum.signIn()
        forum.fake.failNext(500, "internal", "Something went wrong.")
        val vm = PushNotificationsViewModel(forum.sessions, registrar)
        vm.state.until { it.loadProblem != null }

        vm.onEvent(PushNotificationsEvent.Retry)

        assertEquals(
            true,
            vm.state
                .until { it.loadProblem == null && !it.loading }
                .push
                ?.reply,
        )
    }

    @Test
    fun `NT-06 without a distributor the section says how to get one`() {
        distributor = Distributor(DistributorChoice.NoneAvailable)
        val vm = loaded(devices = false)
        registrar.start()
        assertEquals(
            PushStatus.NoDistributor,
            vm.state.until { it.device != PushStatus.Off }.device,
        )
    }
}
