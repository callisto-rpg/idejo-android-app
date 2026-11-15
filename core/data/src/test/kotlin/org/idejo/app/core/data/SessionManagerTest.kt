// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.DiscoveryClient
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.model.ApiError
import org.idejo.app.core.model.TokenGrant
import org.idejo.app.core.testing.FakeIdejo
import org.idejo.app.core.testing.FakePeople
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator

/** The forum the app holds, against the fake forum (MD-03 single-forum, SI-04..12, TH-06). */
class SessionManagerTest {
    @get:Rule val folder = TemporaryFolder()

    private val fake = FakeIdejo()
    private val http = IdejoHttp.forLocalTesting()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val vault = TokenVault(AesGcmBox { key })
    private val file by lazy { folder.root.resolve("app.json") }
    private val stores = mutableListOf<CoroutineScope>()
    private lateinit var store: ForumStore

    @After
    fun stop() {
        fake.close()
        stores.forEach { it.cancel() }
        scope.cancel()
    }

    /** A manager as after a launch; each one reads the same file, as a new process would. */
    private fun manager(): SessionManager {
        val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also(stores::add)
        // The last store must be gone before another opens the same file (DataStore's rule).
        runBlocking { stores.dropLast(1).forEach { it.coroutineContext[Job]?.cancelAndJoin() } }
        store = ForumStore.create(file, storeScope)
        return SessionManager(
            store,
            vault,
            http,
            scope,
            Dispatchers.IO,
        ) { ForumAddress.forLocalTesting(it.toHttpUrl()) }
    }

    private fun found(): DiscoveryResult.Found =
        runBlocking { DiscoveryClient(http).discover(fake.address) as DiscoveryResult.Found }

    private fun grant(): TokenGrant =
        TokenGrant(fake.tokenFor(FakePeople.Ada), tokenId = "tok_1", user = FakePeople.Ada)

    private fun ForumSession.me() = runBlocking { call { me() } }

    @Test
    fun `with no forum yet the app shows the welcome screen`() =
        runBlocking {
            val sessions = manager()
            assertEquals(Launch.Loading, sessions.launch.value)
            sessions.restore()
            assertEquals(Launch.Welcome, sessions.launch.value)
        }

    @Test
    fun `SI-04 a signed-in forum comes back after a relaunch, its token sealed on disk`() =
        runBlocking {
            val grant = grant()
            manager().add(found(), grant)

            assertFalse(file.readText().contains(grant.accessToken.value))
            assertFalse(file.readText().contains(grant.accessToken.value.removePrefix("idj_")))

            val sessions = manager()
            sessions.restore()
            val session = requireNotNull(sessions.active)
            assertEquals(Access.SignedIn, session.access.value)
            assertEquals(fake.address.toString(), session.origin)
            assertEquals(FakePeople.Ada.id, (session.me() as ApiResult.Success).value.id)
        }

    @Test
    fun `SI-05 a visitor holds the forum without a token`() =
        runBlocking {
            val sessions = manager()
            sessions.add(found())
            val session = requireNotNull(sessions.active)
            assertEquals(Access.Visitor, session.access.value)
            assertTrue(session.call { categories() } is ApiResult.Success)
        }

    @Test
    fun `SI-09 a refused token signs the forum out, and it stays signed out after a relaunch`() =
        runBlocking {
            val grant = grant()
            val sessions = manager()
            sessions.add(found(), grant)
            fake.revoke(grant.accessToken)

            val session = requireNotNull(sessions.active)
            assertTrue(session.me() is ApiResult.Failure)
            assertEquals(Access.SignedOut, session.access.value)
            assertTrue(session.record.value.signedOut)
            assertNull(session.record.value.sealedToken)

            val again = manager()
            again.restore()
            assertEquals(Access.SignedOut, requireNotNull(again.active).access.value)
        }

    @Test
    fun `SI-10 a banned account is barred with the server's message`() =
        runBlocking {
            val sessions = manager()
            sessions.add(found(), grant())
            fake.failNext(403, ApiError.BANNED, "You are banned from Garden.")

            val session = requireNotNull(sessions.active)
            session.me()
            assertEquals(
                "You are banned from Garden.",
                (session.access.value as Access.Barred).message,
            )
        }

    @Test
    fun `SI-11 signing out revokes the token and keeps the forum as a visitor`() =
        runBlocking {
            val grant = grant()
            val sessions = manager()
            sessions.add(found(), grant)
            sessions.signOut(fake.address.toString())

            assertTrue(fake.requests.any { it.url.encodedPath == "/api/v1/oauth/revoke" })
            val session = requireNotNull(sessions.active)
            assertEquals(Access.Visitor, session.access.value)
            assertNull(session.record.value.sealedToken)
            assertTrue(
                ForumApiProbe.unauthorized(fake, grant),
            )
        }

    @Test
    fun `MD-03 adding another forum replaces the first, signed out of it`() =
        runBlocking {
            FakeIdejo().use { other ->
                val grant = grant()
                val sessions = manager()
                sessions.add(found(), grant)
                sessions.add(DiscoveryClient(http).discover(other.address) as DiscoveryResult.Found)

                assertEquals(other.address.toString(), requireNotNull(sessions.active).origin)
                assertTrue(ForumApiProbe.unauthorized(fake, grant))
                val state =
                    org.idejo.app.core.api.IdejoJson
                        .decodeFromString(AppState.serializer(), file.readText())
                assertEquals(listOf(other.address.toString()), state.forums.map { it.origin })
            }
        }

    @Test
    fun `SI-11 removing the forum goes back to the welcome screen`() =
        runBlocking {
            val sessions = manager()
            sessions.add(found(), grant())
            sessions.remove(fake.address.toString())
            assertEquals(Launch.Welcome, sessions.launch.value)
        }

    @Test
    fun `TH-06 the forum's theme is kept for the next launch`() =
        runBlocking {
            val sessions = manager()
            sessions.add(found(), grant())
            val session = requireNotNull(sessions.active)
            val server = (session.refreshServer() as ApiResult.Success).value
            assertNotNull(server.theme)

            repeat(50) { if (session.record.value.theme == null) kotlinx.coroutines.delay(20) }
            assertEquals(server.theme, session.record.value.theme)
        }

    @Test
    fun `the member's choices are kept per forum`() =
        runBlocking {
            val sessions = manager()
            sessions.add(found(), grant())
            sessions.updateForum {
                it.copy(
                    themeMode = ThemeMode.Dark,
                    topicView = TopicView.Expanded,
                )
            }

            val again = manager()
            again.restore()
            val record = requireNotNull(again.active).record.value
            assertEquals(ThemeMode.Dark, record.themeMode)
            assertEquals(TopicView.Expanded, record.topicView)
        }

    @Test
    fun `SH-21 Tutorial bubbles are one choice for the app, kept when a forum is removed`() =
        runBlocking {
            val sessions = manager()
            sessions.add(found(), grant())
            AppChoices(store).tutorialBubbles(TutorialBubbles.Off)
            sessions.remove(requireNotNull(sessions.active).origin)
            sessions.add(found())
            assertEquals(TutorialBubbles.Off, AppChoices(store).tutorialBubbles.first())

            manager().restore()
            assertEquals(TutorialBubbles.Off, AppChoices(store).tutorialBubbles.first())
        }
}

/** Whether the fake now refuses [grant]'s token (it was revoked). */
private object ForumApiProbe {
    fun unauthorized(
        fake: FakeIdejo,
        grant: TokenGrant,
    ): Boolean {
        val api =
            org.idejo.app.core.api.ForumApi(
                fake.address,
                "/api/v1",
                { grant.accessToken },
                IdejoHttp.forLocalTesting(),
            )
        val result = runBlocking { api.me() }
        return (result as? ApiResult.Failure)?.problem.let {
            it is org.idejo.app.core.api.ApiProblem.Server && it.signedOut
        }
    }
}
