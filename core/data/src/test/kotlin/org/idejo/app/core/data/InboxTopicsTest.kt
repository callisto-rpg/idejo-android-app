// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.idejo.app.core.api.DiscoveryClient
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.model.MentionState
import org.idejo.app.core.testing.FakeClock
import org.idejo.app.core.testing.FakeIdejo
import org.idejo.app.core.testing.FakePeople
import org.idejo.app.core.testing.FakeSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The inbox's Forums and Topics, built from every connected forum (IB-02 to IB-04). */
class InboxTopicsTest {
    @get:Rule val folder = TemporaryFolder()

    private val clock = FakeClock()
    private val inbox = FakeIdejo(clock, FakeSettings(name = "idejo"))
    private val garden = FakeIdejo(clock)
    private val bees = FakeIdejo(clock, FakeSettings(name = "Bee keepers"))
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
        listOf(inbox, garden, bees).forEach { it.close() }
        scope.cancel()
    }

    private fun found(fake: FakeIdejo) =
        runBlocking { DiscoveryClient(http).discover(fake.address) as DiscoveryResult.Found }

    /** Signed in to the inbox, then [forums] connected. */
    private fun connected(vararg forums: FakeIdejo) =
        runBlocking {
            val url = requireNotNull(coordinator.startInbox(found(inbox), "Pixel 8"))
            coordinator.complete(requireNotNull(inbox.authorize(url, FakePeople.Ada)))
            for (fake in forums) {
                val page = requireNotNull(coordinator.startConnect(found(fake), "Pixel 8"))
                coordinator.complete(requireNotNull(fake.authorize(page, FakePeople.Ada)))
            }
        }

    @Test
    fun `IB-03 IB-04 every connected forum's followed topics, the latest activity first`() =
        runBlocking {
            val tomatoes = garden.addTopic("Tomato plans")
            garden.follow(FakePeople.Ada, tomatoes.id)
            garden.unread(FakePeople.Ada, tomatoes.id, 3, MentionState.Mention)
            clock.advanceSeconds(60)
            val swarm = bees.addTopic("A swarm on Tuesday")
            bees.follow(FakePeople.Ada, swarm.id)
            bees.unread(FakePeople.Ada, swarm.id, 5)
            connected(garden, bees)

            val topics = InboxTopics(sessions).also { it.refresh() }.state.value
            assertEquals(
                listOf("Bee keepers" to "A swarm on Tuesday", "Garden club" to "Tomato plans"),
                topics.topics.map { it.forum to it.topic.title },
            )
            assertEquals(listOf(5, 3), topics.topics.map { it.topic.unreadCount })
            assertTrue(topics.topics.all { it.logoUrl?.endsWith("/logo") == true })
            assertTrue(topics.unreachable.isEmpty())
        }

    @Test
    fun `IB-02 each connected forum's unread count`() =
        runBlocking {
            val tomatoes = garden.addTopic("Tomato plans")
            garden.unread(FakePeople.Ada, tomatoes.id, 4)
            connected(garden, bees)

            val unread =
                InboxTopics(sessions)
                    .also { it.refresh() }
                    .state.value.unread
            assertEquals(4, unread.getValue(garden.address.toString()))
            assertEquals(0, unread.getValue(bees.address.toString()))
        }

    @Test
    fun `IB-04 before idejo 0_3_16, the unread followed topics from GET unread`() =
        runBlocking {
            garden.followedList = false
            val tomatoes = garden.addTopic("Tomato plans")
            val beans = garden.addTopic("Beans")
            garden.follow(FakePeople.Ada, tomatoes.id)
            garden.follow(FakePeople.Ada, beans.id)
            garden.unread(FakePeople.Ada, tomatoes.id, 2)
            connected(garden)

            val topics = InboxTopics(sessions).also { it.refresh() }.state.value
            assertEquals(listOf("Tomato plans"), topics.topics.map { it.topic.title })
        }

    @Test
    fun `IB-04 a forum that fails is named, the others still show`() =
        runBlocking {
            val swarm = bees.addTopic("A swarm on Tuesday")
            bees.follow(FakePeople.Ada, swarm.id)
            connected(garden, bees)
            garden.failNext(503, "unavailable")
            garden.failNext(503, "unavailable")

            val topics = InboxTopics(sessions).also { it.refresh() }.state.value
            assertEquals(listOf("Garden club"), topics.unreachable)
            assertEquals(listOf("A swarm on Tuesday"), topics.topics.map { it.topic.title })
        }

    @Test
    fun `IB-05 a forum's unread change reads its rows again, the others kept`() =
        runBlocking {
            val swarm = bees.addTopic("A swarm on Tuesday")
            bees.follow(FakePeople.Ada, swarm.id)
            connected(garden, bees)
            val topics = InboxTopics(sessions).also { it.refresh() }
            val live = InboxSockets(sessions, topics, scope, settle = 10.milliseconds)
            live.open()
            withTimeout(5.seconds) {
                while (!garden.joined("user:me") || !bees.joined("user:me")) delay(20)
            }
            assertTrue(live.isOpen(garden.address.toString()))

            val tomatoes = garden.addTopic("Tomato plans")
            garden.follow(FakePeople.Ada, tomatoes.id)
            garden.unread(FakePeople.Ada, tomatoes.id, 2)

            val state =
                withTimeout(5.seconds) {
                    topics.state.first { it.unread[garden.address.toString()] == 2 }
                }
            assertEquals(
                setOf("Tomato plans", "A swarm on Tuesday"),
                state.topics.map { it.topic.title }.toSet(),
            )
            live.close()
            assertTrue(!live.isOpen(garden.address.toString()))
        }

    @Test
    fun `IB-07 Private Message in a forum with a chat service starts the inbox's chat`() =
        runBlocking {
            inbox.inbox = true
            inbox.idejoIds =
                mapOf("u_ada" to "ada-sub", requireNotNull(FakePeople.Mina.id) to "mina-sub")
            connected(garden)
            val links = PendingLinks()

            val result = InboxChats(sessions, links).open("mina-sub")

            val id = (result as org.idejo.app.core.api.ApiResult.Success).value.id
            assertEquals(listOf(FakePeople.Mina.id), result.value.with.map { it.id })
            assertTrue(sessions.launch.value is Launch.Inbox)
            val link = requireNotNull(links.take(inbox.address.toString()))
            assertTrue(link.url.endsWith("/p/$id"))

            // An idejo ID the inbox does not know: refused, nothing opens.
            val unknown = InboxChats(sessions, links).open("nobody")
            assertTrue(unknown is org.idejo.app.core.api.ApiResult.Failure)
            assertEquals(null, links.pending.value)
        }
}
