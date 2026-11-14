// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.idejo.app.core.api.DiscoveryClient
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.IdejoClient
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.testing.FakeIdejo
import org.idejo.app.core.testing.FakePeople
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator

/** SI-03 and SI-04 through the coordinator, with the fake forum's sign-in page. */
class SignInCoordinatorTest {
    @get:Rule val folder = TemporaryFolder()

    private val fake = FakeIdejo()
    private val http = IdejoHttp.forLocalTesting()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val sessions by lazy {
        SessionManager(
            ForumStore.create(folder.root.resolve("app.json"), scope),
            TokenVault(AesGcmBox { key }),
            http,
            scope,
            Dispatchers.IO,
        ) { ForumAddress.forLocalTesting(it.toHttpUrl()) }
    }
    private val coordinator by lazy { SignInCoordinator(sessions, http) }

    @After
    fun stop() {
        fake.close()
        scope.cancel()
    }

    private fun found() =
        runBlocking {
            DiscoveryClient(http).discover(fake.address) as DiscoveryResult.Found
        }

    @Test
    fun `SI-03 SI-04 Allow on the forum's page signs the member in`() =
        runBlocking {
            val url = requireNotNull(coordinator.start(found(), "Pixel 8"))
            assertEquals(SignInState.Waiting("Garden club"), coordinator.state.value)
            assertEquals("Pixel 8", url.queryParameter("device_name"))

            coordinator.complete(requireNotNull(fake.authorize(url, FakePeople.Ada)))

            assertEquals(SignInState.Idle, coordinator.state.value)
            val session = requireNotNull(sessions.active)
            assertEquals(Access.SignedIn, session.access.value)
            assertEquals(FakePeople.Ada.id, session.record.value.userId)
        }

    @Test
    fun `SI-03 Cancel returns quietly, and nothing is added`() =
        runBlocking {
            val url = requireNotNull(coordinator.start(found(), "Pixel 8"))
            coordinator.complete(requireNotNull(fake.authorize(url, FakePeople.Ada, allow = false)))
            assertEquals(SignInState.Idle, coordinator.state.value)
            assertNull(sessions.active)
        }

    @Test
    fun `SI-03 a redirect for another sign-in is ignored`() =
        runBlocking {
            coordinator.start(found(), "Pixel 8")
            coordinator.complete(IdejoClient.REDIRECT_URI + "?code=abc&state=someone-else")
            assertEquals(SignInState.Waiting("Garden club"), coordinator.state.value)
        }

    @Test
    fun `a redirect after the app was closed says the sign-in expired`() =
        runBlocking {
            coordinator.complete(IdejoClient.REDIRECT_URI + "?code=abc&state=xyz")
            assertEquals(
                SignInState.Failed("", SignInState.Reason.Expired),
                coordinator.state.value,
            )
        }

    @Test
    fun `a closed Custom Tab cancels the wait`() {
        coordinator.start(found(), "Pixel 8")
        coordinator.cancelIfWaiting()
        assertEquals(SignInState.Idle, coordinator.state.value)
    }

    @Test
    fun `SI-05 SI-09 a visitor signs in again to the forum the app holds`() =
        runBlocking {
            sessions.add(found())
            val session = requireNotNull(sessions.active)
            assertEquals(Access.Visitor, session.access.value)

            val url = requireNotNull(coordinator.startAgain(session, "Pixel 8"))
            coordinator.complete(requireNotNull(fake.authorize(url, FakePeople.Ada)))

            assertEquals(Access.SignedIn, requireNotNull(sessions.active).access.value)
            assertTrue(sessions.active !== session)
        }
}
