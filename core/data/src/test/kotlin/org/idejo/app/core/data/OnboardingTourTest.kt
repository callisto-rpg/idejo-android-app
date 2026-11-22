// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.idejo.app.core.api.DiscoveryClient
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.model.TokenGrant
import org.idejo.app.core.testing.FakeIdejo
import org.idejo.app.core.testing.FakePeople
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator

/** The onboarding tour's state (IX-18): from `GET /me`, moved on by the composer. */
class OnboardingTourTest {
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
    private val ada = FakePeople.Ada

    @After
    fun stop() {
        sessions.active?.socket?.disconnect()
        fake.close()
        scope.cancel()
    }

    private suspend fun signedIn(): ForumSession {
        val found = DiscoveryClient(http).discover(fake.address) as DiscoveryResult.Found
        sessions.add(found, TokenGrant(fake.tokenFor(ada), tokenId = "tok_1", user = ada))
        return requireNotNull(sessions.active)
    }

    private suspend fun OnboardingTour.until(test: (OnboardingTour.State) -> Boolean) =
        withTimeout(WAIT) { state.first(test) }

    @Test
    fun `IX-18 a member on the tour is led to the onboarding topic, and three Enters end it`() =
        runBlocking<Unit> {
            fake.welcomed(ada)
            fake.touring(ada, "t_1", question = "Why are you here")
            val session = signedIn()
            val tour = session.tour
            session.me.refresh()

            val on = tour.until { it.active }
            assertEquals("t_1", on.onboarding?.topicId)
            assertEquals("Why are you here", on.onboarding?.question)

            // Another topic's composer moves nothing.
            tour.composer("t_2", open = true)
            tour.closed("t_2")
            assertFalse(tour.state.value.open || tour.state.value.finished)

            tour.composer("t_1", open = true)
            assertTrue(tour.state.value.open)
            tour.enteredOnce("t_1")
            tour.enteredOnce("t_1")
            assertEquals(2, tour.state.value.loneEnters)

            tour.closed("t_1")
            assertTrue(tour.state.value.finished)
            // Finished on the server at once; the last card stays until Close.
            withTimeout(WAIT) { session.me.state.first { it?.onboarding == null } }
            assertFalse(fake.touring(ada))
            assertTrue(tour.state.value.finished)

            tour.dismiss()
            assertFalse(tour.state.value.active || tour.state.value.finished)
        }

    @Test
    fun `IX-18 closing the welcome card starts the tour`() =
        runBlocking<Unit> {
            fake.newMember(ada)
            fake.touring(ada, "t_1")
            val session = signedIn()
            session.me.refresh()
            assertFalse(session.tour.state.value.active)

            val welcome = Welcome(session, scope)
            welcome.continueWelcome().join()

            assertTrue(session.tour.until { it.active }.active)
        }

    @Test
    fun `IX-18 no tour without onboarding from the server`() =
        runBlocking<Unit> {
            fake.welcomed(ada)
            val session = signedIn()
            session.me.refresh()
            withTimeout(WAIT) { session.me.state.first { it != null } }
            assertFalse(session.tour.state.value.active)
            session.tour.closed("t_1")
            assertFalse(session.tour.state.value.finished)
        }

    private companion object {
        const val WAIT = 5_000L
    }
}
