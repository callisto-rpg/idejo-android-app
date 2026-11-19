// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.DiscoveryClient
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.model.Community
import org.idejo.app.core.testing.FakeIdejo
import org.idejo.app.core.testing.FakePeople
import org.idejo.app.core.testing.FakeSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator
import kotlin.time.Duration.Companion.seconds

/** idejo ID mode (MD-02): the inbox signed in, the group's forums connected (SI-06). */
class IdejoIdModeTest {
    @get:Rule val folder = TemporaryFolder()

    private val inbox = FakeIdejo(settings = FakeSettings(name = "idejo"))
    private val garden = FakeIdejo()
    private val bees = FakeIdejo(settings = FakeSettings(name = "Bee keepers"))
    private val http = IdejoHttp.forLocalTesting()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    /** One file, one store, as the app's process has; each manager is a launch. */
    private val store by lazy { ForumStore.create(folder.root.resolve("app.json"), scope) }

    private fun manager() =
        SessionManager(
            store,
            TokenVault(AesGcmBox { key }),
            http,
            scope,
            Dispatchers.IO,
        ) { ForumAddress.forLocalTesting(it.toHttpUrl()) }

    private val sessions by lazy { manager() }
    private val coordinator by lazy { SignInCoordinator(sessions, http) }

    @After
    fun stop() {
        listOf(inbox, garden, bees).forEach { it.close() }
        scope.cancel()
    }

    private fun found(fake: FakeIdejo) =
        runBlocking { DiscoveryClient(http).discover(fake.address) as DiscoveryResult.Found }

    private fun signInToInbox() =
        runBlocking {
            val url = requireNotNull(coordinator.startInbox(found(inbox), "Pixel 8"))
            coordinator.complete(requireNotNull(inbox.authorize(url, FakePeople.Ada)))
        }

    /** One forum connected through its sign-in page; what the coordinator said of it. */
    private fun connect(
        fake: FakeIdejo,
        allow: Boolean = true,
    ): SignInOutcome =
        runBlocking {
            val outcome = async { withTimeout(5.seconds) { coordinator.outcomes.first() } }
            val url = requireNotNull(coordinator.startConnect(found(fake), "Pixel 8"))
            coordinator.complete(requireNotNull(fake.authorize(url, FakePeople.Ada, allow)))
            outcome.await()
        }

    private val launch get() = sessions.launch.value
    private val held get() = runBlocking { sessions.state.first() }

    @Test
    fun `SI-06 signed in to the inbox, the app is in idejo ID mode with the inbox on screen`() {
        signInToInbox()
        val shown = launch as Launch.Inbox
        assertEquals("idejo", shown.session.name)
        assertEquals(Access.SignedIn, shown.session.access.value)
        assertEquals(AppMode.IdejoId, held.mode)
        assertEquals(inbox.address.toString(), held.inbox?.origin)
    }

    @Test
    fun `SI-06 each forum connected is held with its own token, the inbox still on screen`() {
        signInToInbox()
        assertEquals(SignInOutcome(garden.address.toString(), true), connect(garden))
        assertEquals(SignInOutcome(bees.address.toString(), true), connect(bees))

        assertTrue(launch is Launch.Inbox)
        assertEquals(listOf("Garden club", "Bee keepers"), held.forums.map { it.name })
        assertTrue(held.forums.all { it.sealedToken != null && it.userId == FakePeople.Ada.id })
    }

    @Test
    fun `SI-06 a forum skipped stays not connected`() {
        signInToInbox()
        assertEquals(
            SignInOutcome(garden.address.toString(), false),
            connect(garden, allow = false),
        )
        assertTrue(held.forums.isEmpty())
    }

    @Test
    fun `MD-02 a forum opens from the inbox, back leads to the inbox, and a launch restores it`() =
        runBlocking {
            signInToInbox()
            connect(garden)

            sessions.enter(garden.address.toString())
            assertEquals("Garden club", (launch as Launch.Forum).session.name)
            assertEquals(Access.SignedIn, (launch as Launch.Forum).session.access.value)
            // Next launch, the forum open again (MD-05).
            val again = manager().also { it.restore() }
            assertEquals("Garden club", (again.launch.value as Launch.Forum).session.name)

            sessions.openInbox()
            assertTrue(launch is Launch.Inbox)
            val later = manager().also { it.restore() }
            assertTrue(later.launch.value is Launch.Inbox)
        }

    @Test
    fun `MD-02 adding a forum by address keeps the others, removing one leads to the inbox`() =
        runBlocking {
            signInToInbox()
            connect(garden)

            sessions.add(found(bees))
            assertEquals(listOf("Garden club", "Bee keepers"), held.forums.map { it.name })
            assertEquals("Bee keepers", (launch as Launch.Forum).session.name)

            sessions.remove(bees.address.toString())
            assertTrue(launch is Launch.Inbox)
            assertEquals(listOf("Garden club"), held.forums.map { it.name })
        }

    @Test
    fun `MD-03 single-forum mode still keeps one forum`() =
        runBlocking {
            sessions.add(found(garden))
            sessions.add(found(bees))
            assertEquals(AppMode.Single, held.mode)
            assertEquals(listOf("Bee keepers"), held.forums.map { it.name })
        }

    private val group by lazy {
        GroupForums(sessions, coordinator, DiscoveryClient(http), "Pixel 8") {
            ForumAddress.forLocalTesting(it.toHttpUrl())
        }
    }

    private fun joinedBoth() {
        inbox.communities =
            listOf(
                Community(
                    "Garden club",
                    garden.address.toString(),
                    garden.address.resolve("/logo")?.toString(),
                ),
                Community("Bee keepers", bees.address.toString()),
            )
    }

    private fun joined() = runBlocking { (group.joined() as ApiResult.Success).value }

    @Test
    fun `SI-06 the inbox lists the forums joined, none connected yet`() {
        joinedBoth()
        signInToInbox()
        assertEquals(
            listOf(
                GroupForum(
                    "Garden club",
                    garden.address.toString(),
                    garden.address.resolve("/logo")?.toString(),
                ),
                GroupForum("Bee keepers", bees.address.toString()),
            ),
            joined(),
        )
    }

    @Test
    fun `SI-06 Connect runs the forum's sign-in page, then the forum shows connected`() =
        runBlocking {
            joinedBoth()
            signInToInbox()
            val pages = mutableListOf<String>()
            for (forum in joined()) {
                val fake = if (forum.name == "Garden club") garden else bees
                val done =
                    group.connect(forum) { url ->
                        pages += url.host + ":" + url.port
                        coordinator.complete(requireNotNull(fake.authorize(url, FakePeople.Ada)))
                        true
                    }
                assertTrue(done)
            }
            assertEquals(
                listOf(garden, bees).map { it.address.host + ":" + it.address.origin.port },
                pages,
            )
            assertEquals(listOf(true, true), joined().map { it.connected })
        }

    @Test
    fun `SI-06 a page closed without an answer leaves the forum not connected`() =
        runBlocking {
            joinedBoth()
            signInToInbox()
            val done =
                group.connect(joined().first()) {
                    coordinator.cancelIfWaiting()
                    true
                }
            assertFalse(done)
            assertEquals(listOf(false, false), joined().map { it.connected })
            assertEquals(SignInState.Idle, coordinator.state.value)
        }
}
