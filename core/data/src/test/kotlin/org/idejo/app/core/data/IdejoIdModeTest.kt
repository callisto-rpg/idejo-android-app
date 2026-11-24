// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.DirectoryClient
import org.idejo.app.core.api.DiscoveryClient
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.model.Community
import org.idejo.app.core.model.DirectoryForum
import org.idejo.app.core.testing.FakeIdejo
import org.idejo.app.core.testing.FakePeople
import org.idejo.app.core.testing.FakeSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator
import kotlin.time.Duration.Companion.milliseconds
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

    private fun FakeIdejo.revokeAsked() =
        requests.any {
            it.url.encodedPath ==
                "/api/v1/oauth/revoke"
        }

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
    fun `SH-15 removing another forum than the one open keeps it open`() =
        runBlocking {
            signInToInbox()
            connect(garden)
            connect(bees)
            sessions.enter(garden.address.toString())
            val open = (launch as Launch.Forum).session

            sessions.remove(bees.address.toString())
            assertEquals(open, (launch as Launch.Forum).session)
            assertEquals(listOf("Garden club"), held.forums.map { it.name })
            assertEquals(garden.address.toString(), held.active)
        }

    @Test
    fun `MD-04 leaving idejo ID mode keeps one forum, revokes the other tokens`() =
        runBlocking {
            signInToInbox()
            connect(garden)
            connect(bees)
            val left = async { withTimeout(5.seconds) { sessions.signedOut.take(2).toList() } }

            sessions.leaveIdejoId(garden.address.toString())

            assertEquals(AppMode.Single, held.mode)
            assertEquals(null, held.inbox)
            assertEquals(listOf("Garden club"), held.forums.map { it.name })
            assertTrue(sessions.connected(garden.address.toString()))
            assertEquals("Garden club", (launch as Launch.Forum).session.name)
            assertEquals(
                setOf(bees.address.toString(), inbox.address.toString()),
                left.await().toSet(),
            )
            assertTrue(bees.revokeAsked())
            assertTrue(inbox.revokeAsked())
            assertFalse(garden.revokeAsked())
        }

    @Test
    fun `MD-04 leaving idejo ID mode without a forum opens the welcome screen`() =
        runBlocking {
            signInToInbox()
            sessions.leaveIdejoId(null)
            assertEquals(Launch.Welcome, launch)
            assertEquals(AppMode.Single, held.mode)
        }

    @Test
    fun `MD-03 single-forum mode still keeps one forum`() =
        runBlocking {
            sessions.add(found(garden))
            sessions.add(found(bees))
            assertEquals(AppMode.Single, held.mode)
            assertEquals(listOf("Bee keepers"), held.forums.map { it.name })
        }

    @Test
    fun `PC-02 MD-03 from single-forum mode, idejo ID keeps the forum and its token`() =
        runBlocking {
            val page = requireNotNull(coordinator.start(found(garden), "Pixel 8"))
            coordinator.complete(requireNotNull(garden.authorize(page, FakePeople.Ada)))
            assertEquals(AppMode.Single, held.mode)

            signInToInbox()
            assertEquals(AppMode.IdejoId, held.mode)
            assertTrue(launch is Launch.Inbox)
            assertEquals(listOf("Garden club"), held.forums.map { it.name })
            assertTrue(sessions.connected(garden.address.toString()))
        }

    private val group by lazy {
        GroupForums(
            sessions,
            coordinator,
            DiscoveryClient(http),
            DirectoryClient(http),
            "Pixel 8",
        ) {
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

    private fun joined() = runBlocking { (group.refresh() as ApiResult.Success).value }

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
                    new = true,
                ),
                GroupForum("Bee keepers", bees.address.toString(), new = true),
            ),
            joined(),
        )
    }

    @Test
    fun `SH-12 the inbox says which forums are favourites`() {
        inbox.communities =
            listOf(
                Community("Garden club", garden.address.toString(), favourite = true),
                Community("Bee keepers", bees.address.toString(), favourite = false),
            )
        signInToInbox()
        assertEquals(listOf(true, false), joined().map { it.favourite })
    }

    @Test
    fun `IB-02 the Forums tab's menu shows only where the inbox answers it`() =
        runBlocking {
            joinedBoth()
            signInToInbox()
            assertFalse(group.hasMenu())
            inbox.forumMenu = true
            // Read now, not from the discovery kept at sign-in: the inbox may have updated.
            assertTrue(group.hasMenu())
        }

    @Test
    fun `IB-02 a forum marked as a favourite and back, through the inbox`() =
        runBlocking {
            joinedBoth()
            inbox.forumMenu = true
            signInToInbox()
            val garden = joined().first()
            assertTrue(group.favourite(garden, true))
            assertEquals(
                true,
                group.joined.value
                    .first()
                    .favourite,
            )
            assertEquals(listOf(true, null), joined().map { it.favourite })
            assertTrue(group.favourite(garden, false))
            assertEquals(listOf(false, null), joined().map { it.favourite })
        }

    @Test
    fun `IB-02 leaving a forum takes it off the list and out of the app`() =
        runBlocking {
            joinedBoth()
            inbox.forumMenu = true
            signInToInbox()
            val forum = joined().first()
            group.connect(forum) { url ->
                coordinator.complete(requireNotNull(garden.authorize(url, FakePeople.Ada)))
                true
            }
            val connected = joined().first()
            assertTrue(connected.connected)
            assertTrue(group.leave(connected))
            assertEquals(listOf(garden.address.toString()), inbox.left)
            assertEquals(listOf("Bee keepers"), group.joined.value.map { it.name })
            assertFalse(sessions.connected(garden.address.toString()))
            assertEquals(listOf("Bee keepers"), joined().map { it.name })
        }

    @Test
    fun `IB-02 an inbox without the menu refuses, and nothing changes`() =
        runBlocking {
            joinedBoth()
            signInToInbox()
            val forum = joined().first()
            assertFalse(group.favourite(forum, true))
            assertFalse(group.leave(forum))
            assertEquals(listOf("Garden club", "Bee keepers"), joined().map { it.name })
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
                assertEquals(fake.address.toString(), done)
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
            assertNull(done)
            assertEquals(listOf(false, false), joined().map { it.connected })
            assertEquals(SignInState.Idle, coordinator.state.value)
        }

    @Test
    fun `SI-07 a forum joined since last seen is new until seen, also after a launch`() =
        runBlocking {
            joinedBoth()
            signInToInbox()
            group.refresh()
            group.seen()
            assertFalse(group.anyNew.first())

            val choir = FakeIdejo(settings = FakeSettings(name = "Village choir"))
            try {
                inbox.communities += Community("Village choir", choir.address.toString())
                group.refresh()
                assertEquals(
                    listOf("Village choir"),
                    group.joined.value
                        .filter { it.new }
                        .map { it.name },
                )
                assertTrue(group.anyNew.first())

                // Next launch, still new: what was seen is kept on the device.
                val later =
                    GroupForums(
                        manager(),
                        coordinator,
                        DiscoveryClient(http),
                        DirectoryClient(http),
                        "Pixel 8",
                    ) {
                        ForumAddress.forLocalTesting(it.toHttpUrl())
                    }
                later.refresh()
                assertEquals(
                    listOf("Village choir"),
                    later.joined.value
                        .filter { it.new }
                        .map { it.name },
                )

                group.seen()
                assertFalse(group.anyNew.first())
            } finally {
                choir.close()
            }
        }

    @Test
    fun `SI-07 the list is read at launch and again on schedule`() =
        runBlocking<Unit> {
            joinedBoth()
            signInToInbox()
            val job = group.start(scope, every = 200.milliseconds)
            try {
                withTimeout(5.seconds) { group.joined.first { it.size == 2 } }
                inbox.communities = inbox.communities.take(1)
                withTimeout(5.seconds) { group.joined.first { it.size == 1 } }
            } finally {
                job.cancel()
            }
        }

    @Test
    fun `SI-07 a forum connected does not count as new`() =
        runBlocking {
            joinedBoth()
            signInToInbox()
            group.refresh()
            connect(garden)
            group.refresh()
            assertEquals(listOf(true, false), group.joined.value.map { it.connected })
            val waiting = group.joined.value.filter { it.new && !it.connected }
            assertEquals(listOf("Bee keepers"), waiting.map { it.name })
        }

    @Test
    fun `SI-07 outside idejo ID mode there is nothing to read`() =
        runBlocking {
            sessions.add(found(garden))
            assertTrue(group.refresh() is ApiResult.Failure)
            assertTrue(group.joined.value.isEmpty())
        }

    private fun listed(
        fake: FakeIdejo,
        members: Int,
    ): DirectoryForum {
        val name = if (fake === garden) "Garden club" else "Bee keepers"
        return DirectoryForum(name, fake.address.toString(), fake.address.host, members = members)
    }

    @Test
    fun `SI-08 Find more forums searches the group's directory, page by page`() =
        runBlocking {
            inbox.directory =
                listOf(listed(garden, 12), listed(bees, 4)) +
                (1..25).map {
                    DirectoryForum(
                        "Forum $it",
                        "https://f$it.example.org/",
                        "f$it.example.org",
                    )
                }
            signInToInbox()
            assertTrue(group.hasDirectory())

            val first = (group.directory("", 1) as ApiResult.Success).value
            assertEquals(20, first.forums.size)
            assertEquals(27, first.total)
            assertTrue(first.more)
            val second = (group.directory("", 2) as ApiResult.Success).value
            assertEquals(7, second.forums.size)
            assertFalse(second.more)

            val bees = (group.directory("bee", 1) as ApiResult.Success).value
            assertEquals(listOf("Bee keepers"), bees.forums.map { it.name })
            assertEquals(4, bees.forums.single().members)
        }

    @Test
    fun `SI-08 Join signs in to the forum, which is then held and listed, its origin answered`() =
        runBlocking {
            inbox.directory = listOf(listed(garden, 12))
            signInToInbox()
            val forum = (group.directory("", 1) as ApiResult.Success).value.forums.single()
            // Signing in there with the idejo ID makes the member one of its members.
            inbox.communities = listOf(Community("Garden club", garden.address.toString()))
            val joined =
                group.join(forum) { url ->
                    coordinator.complete(requireNotNull(garden.authorize(url, FakePeople.Ada)))
                    true
                }
            assertEquals(garden.address.toString(), joined)
            assertEquals(listOf("Garden club"), held.forums.map { it.name })
            assertEquals(listOf(true), group.joined.value.map { it.connected })
            assertTrue(launch is Launch.Inbox)
        }

    @Test
    fun `SI-08 a group without a directory has no Find more forums`() =
        runBlocking {
            signInToInbox()
            assertFalse(group.hasDirectory())
            assertTrue(group.directory("", 1) is ApiResult.Failure)
        }
}
