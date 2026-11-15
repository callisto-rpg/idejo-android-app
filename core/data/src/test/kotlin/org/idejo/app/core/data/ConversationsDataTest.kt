// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.DiscoveryClient
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.model.Commit
import org.idejo.app.core.model.TokenGrant
import org.idejo.app.core.realtime.SocketState
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
import kotlin.time.Duration.Companion.seconds

/** Private conversations against the fake forum (PC-01, PC-03, PC-04). */
class ConversationsDataTest {
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
    private val mina = FakePeople.Mina

    @After
    fun stop() {
        sessions.active?.socket?.disconnect()
        fake.close()
        scope.cancel()
    }

    private suspend fun connected(): ForumSession {
        val found = DiscoveryClient(http).discover(fake.address) as DiscoveryResult.Found
        sessions.add(found, TokenGrant(fake.tokenFor(ada), tokenId = "tok_1", user = ada))
        val session = requireNotNull(sessions.active)
        session.socket.connect()
        withTimeout(5.seconds) { session.socket.state.first { it == SocketState.Connected } }
        return session
    }

    private suspend fun <T> StateFlow<T>.until(done: (T) -> Boolean): T =
        withTimeout(5.seconds) { first(done) }

    private suspend fun eventually(check: () -> Boolean) {
        withTimeout(5.seconds) { while (!check()) delay(20) }
    }

    @Test
    fun `PC-01 the list counts the conversations with news and follows new messages`() =
        runBlocking<Unit> {
            val withMina = fake.conversation(ada, mina)
            fake.post(mina, withMina, "Seeds for you")
            val conversations = ForumConversations(connected(), scope).also { it.start() }

            val loaded = conversations.state.until { !it.loading && it.available }
            assertEquals(listOf("Mina Kader"), loaded.conversations.map { it.name })
            assertEquals(1, loaded.withNews)
            assertEquals(
                "Seeds for you.",
                loaded.conversations
                    .single()
                    .lastMessage
                    ?.excerpt,
            )

            // A second conversation's first message arrives through `conversation.updated`.
            fake.clock.advanceSeconds(60)
            val withLeo = fake.conversation(ada, FakePeople.Leo)
            fake.post(FakePeople.Leo, withLeo, "Spare pots")
            val live = conversations.state.until { it.withNews == 2 }
            assertEquals(listOf(withLeo, withMina), live.conversations.map { it.id })
        }

    @Test
    fun `PC-01 a conversation reads and writes as a topic, on its channel, with no slow mode`() =
        runBlocking<Unit> {
            val id = fake.conversation(ada, mina)
            fake.post(mina, id, "Hello Ada")
            val session = connected()
            val reader = TopicReader(session, id, scope, private = true)
            reader.start()

            val loaded = reader.state.until { !it.loading }
            assertEquals("Mina Kader", loaded.topic?.title)
            assertEquals(listOf("Hello Ada."), loaded.messages.map { it.bodyMd })
            eventually { fake.joined("conversation:$id") }
            assertFalse(fake.joined("topic:$id"))

            val composer = (session.call { composer(id) } as ApiResult.Success).value
            assertEquals(0, composer.windowSeconds)
            assertTrue(
                session.call {
                    commit(
                        id,
                        Commit("Thanks", breakMessage = true),
                    )
                } is ApiResult.Success,
            )
            assertTrue(
                session.call {
                    commit(
                        id,
                        Commit("See you", breakMessage = true),
                    )
                } is ApiResult.Success,
            )

            fake.post(mina, id, "Bye")
            reader.state.until { s -> s.messages.any { it.bodyMd == "Bye." } }
            reader.stop()
        }

    @Test
    fun `PC-03 Private Message starts the conversation once, never with someone who refuses`() =
        runBlocking<Unit> {
            val session = connected()
            assertTrue((session.call { user(mina.id!!) } as ApiResult.Success).value.mayMessage)
            val first = (session.call { startConversation(mina.id!!) } as ApiResult.Success).value
            val again = (session.call { startConversation(mina.id!!) } as ApiResult.Success).value
            assertEquals(first.id, again.id)
            assertEquals(listOf("Mina Kader"), first.with.map { it.displayName })

            fake.acceptPrivateMessages(FakePeople.Leo, false)
            assertFalse(
                (session.call { user(FakePeople.Leo.id!!) } as ApiResult.Success).value.mayMessage,
            )
            assertTrue(session.call { startConversation(FakePeople.Leo.id!!) } is ApiResult.Failure)

            val off = session.call { acceptPrivateMessages(false) }
            assertFalse((off as ApiResult.Success).value.acceptsPrivateMessages)
            assertFalse((session.call { me() } as ApiResult.Success).value.acceptsPrivateMessages)
        }

    @Test
    fun `PC-04 conversations never show in the sidebar's categories, Unread or search`() =
        runBlocking<Unit> {
            val id = fake.conversation(ada, mina)
            fake.post(mina, id, "Secret tomatoes")
            val session = connected()

            val sidebar = ForumSidebar(session, scope).also { it.start() }
            val state = sidebar.state.until { !it.loading }
            assertFalse(state.topics.any { it.id == id })
            assertEquals(0, state.unread.count)

            val feed = UnreadFeed(session, scope).also { it.start() }
            val unread = feed.state.until { !it.others.loading }
            assertFalse((unread.following + unread.others.items).any { it.id == id })

            val hits = (session.call { search("Secret") } as ApiResult.Success).value.data
            assertTrue(hits.none { it.topicId == id })
            sidebar.close()
        }
}
