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
import org.idejo.app.core.api.DiscoveryClient
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.model.Poll
import org.idejo.app.core.model.PollOption
import org.idejo.app.core.model.TokenGrant
import org.idejo.app.core.realtime.SocketState
import org.idejo.app.core.testing.FakeIdejo
import org.idejo.app.core.testing.FakePeople
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator
import kotlin.time.Duration.Companion.seconds

/** The topic page's messages against the fake forum (TV-01 to TV-06, TV-10, TV-12). */
class TopicDataTest {
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

    private fun signedIn(): ForumSession =
        runBlocking {
            val found = DiscoveryClient(http).discover(fake.address) as DiscoveryResult.Found
            val grant = TokenGrant(fake.tokenFor(ada), tokenId = "tok_1", user = ada)
            sessions.add(found, grant)
            requireNotNull(sessions.active)
        }

    private suspend fun connected(): ForumSession {
        val session = signedIn()
        session.socket.connect()
        withTimeout(5.seconds) { session.socket.state.first { it == SocketState.Connected } }
        return session
    }

    private suspend fun <T> StateFlow<T>.until(done: (T) -> Boolean): T =
        withTimeout(5.seconds) { first(done) }

    private suspend fun eventually(check: () -> Boolean) {
        withTimeout(5.seconds) { while (!check()) delay(20) }
    }

    /** A topic of [count] messages after its opening one, Mina's. */
    private fun longTopic(count: Int): String {
        val topic = fake.addTopic("Tomato plans for spring")
        repeat(count) { fake.post(mina, topic.id, "Message $it") }
        return topic.id
    }

    @Test
    fun `TV-01 the opening message on top, then the window from the first unread`() =
        runBlocking {
            val id = longTopic(60)
            fake.unread(ada, id, 5)
            val reader = TopicReader(signedIn(), id, scope)

            reader.load()
            val state = reader.state.until { !it.loading }

            assertEquals("Tomato plans for spring", state.topic?.title)
            assertEquals("Tomato plans for spring", state.opening?.bodyMd)
            val first = fake.messages(id)[56]
            assertEquals(first.id, state.unread?.messageId)
            assertEquals(5, state.unread?.count)
            assertEquals(first.id, state.landing)
            assertTrue(state.messages.any { it.id == first.id })
            assertTrue(state.atEnd)

            // TV-07: mentions glow from there on, after the line has faded too.
            assertEquals(first.id, state.glowFrom)
            reader.unreadShown()
            val faded = reader.state.until { it.unread == null }
            assertEquals(first.id, faded.glowFrom)
        }

    @Test
    fun `TV-01 with nothing unread the window is the end, the opening message apart`() =
        runBlocking {
            val id = longTopic(60)
            val reader = TopicReader(signedIn(), id, scope)

            reader.load()
            val state = reader.state.until { !it.loading }

            assertNull(state.unread)
            assertEquals(50, state.messages.size)
            assertEquals("Message 59.", state.messages.last().bodyMd)
            assertEquals(10, state.earlier)
            assertEquals(0, state.later)
        }

    @Test
    fun `TV-05 IX-09 a window around a message loads both ways, counting what is left`() =
        runBlocking {
            val id = longTopic(100)
            val middle = fake.messages(id)[50].id
            val reader = TopicReader(signedIn(), id, scope, landing = middle)

            reader.load()
            val around = reader.state.until { !it.loading }
            assertEquals(41, around.messages.size)
            assertEquals(39, around.earlier)
            assertEquals(20, around.later)
            assertFalse(around.atEnd)

            reader.later()
            val later = reader.state.until { !it.loadingLater && it.messages.size > 41 }
            assertTrue(later.atEnd)
            assertEquals(
                fake.messages(id).last().id,
                later.messages.last().id,
            )

            reader.earlier()
            val earlier = reader.state.until { !it.loadingEarlier && it.earlier == 0 }
            assertEquals(fake.messages(id).map { it.id }, earlier.messages.map { it.id })
            assertNull("the opening message is in the window now", earlier.opening)
        }

    @Test
    fun `TV-03 TV-12 messages come live while the topic is open, and stop when it closes`() =
        runBlocking {
            val id = longTopic(2)
            val reader = TopicReader(connected(), id, scope)
            reader.start()
            reader.state.until { !it.loading }
            eventually { fake.joined("topic:$id") }

            val message = fake.post(mina, id, "Sungold every year")
            val live = reader.state.until { s -> s.messages.any { it.id == message.id } }
            assertEquals("Sungold every year.", live.messages.last().bodyMd)

            reader.stop()
            eventually { !fake.joined("topic:$id") }
        }

    @Test
    fun `TV-05 new messages are counted while the window does not reach the end`() =
        runBlocking {
            val id = longTopic(100)
            val reader = TopicReader(connected(), id, scope, landing = fake.messages(id)[10].id)
            reader.start()
            val shown = reader.state.until { !it.loading }
            eventually { fake.joined("topic:$id") }

            fake.post(mina, id, "Something new")

            val counted = reader.state.until { it.newBelow == 1 }
            assertEquals(shown.later + 1, counted.later)
            assertEquals(shown.messages, counted.messages)

            reader.end()
            val end = reader.state.until { !it.loading && it.newBelow == 0 }
            assertEquals("Something new.", end.messages.last().bodyMd)
        }

    @Test
    fun `TV-05 a new message is counted once, though it opens and then closes`() =
        runBlocking {
            val id = longTopic(100)
            val reader = TopicReader(connected(), id, scope, landing = fake.messages(id)[10].id)
            reader.start()
            val shown = reader.state.until { !it.loading }
            eventually { fake.joined("topic:$id") }

            fake.post(mina, id, "Something new")
            fake.post(mina, id, "And another")
            reader.state.until { it.newBelow >= 2 }
            // Each message's later events (its closing) arrive after it was counted.
            delay(SETTLE)

            assertEquals(2, reader.state.value.newBelow)
            assertEquals(shown.later + 2, reader.state.value.later)
        }

    @Test
    fun `TV-04 after a reconnect the changes missed are caught up`() =
        runBlocking<Unit> {
            val id = longTopic(2)
            val session = connected()
            val reader = TopicReader(session, id, scope)
            reader.start()
            reader.state.until { !it.loading }
            eventually { fake.joined("topic:$id") }

            session.socket.disconnect()
            eventually { !fake.joined("topic:$id") }
            val missed = fake.post(mina, id, "Written while away")
            session.socket.connect()

            reader.state.until { s -> s.messages.any { it.id == missed.id } }
        }

    @Test
    fun `TV-06 the read position follows what was seen, never back`() =
        runBlocking<Unit> {
            val id = longTopic(10)
            fake.unread(ada, id, 6)
            val reader = TopicReader(signedIn(), id, scope)
            reader.load()
            val state = reader.state.until { !it.loading }
            val messages = state.messages

            reader.seen(messages[messages.size - 4].id)
            reader.state.until { it.topic?.unreadCount == 3 }
            reader.seen(messages[1].id)
            delay(1_500)
            assertEquals(
                3,
                reader.state.value.topic
                    ?.unreadCount,
            )

            reader.seen(messages.last().id)
            reader.state.until { it.topic?.unreadCount == 0 }
        }

    @Test
    fun `TV-10 Follow and Unfollow`() =
        runBlocking {
            val id = longTopic(1)
            val reader = TopicReader(signedIn(), id, scope)
            reader.load()
            reader.state.until { !it.loading }

            reader.follow(true)
            reader.state.until { it.topic?.followed == true }
            assertTrue(fake.follows(ada, id))

            reader.follow(false)
            reader.state.until { it.topic?.followed == false }
            assertFalse(fake.follows(ada, id))
        }

    @Test
    fun `TV-01 a topic the member may not view is gone, never told which`() =
        runBlocking {
            val reader = TopicReader(signedIn(), "t_missing", scope)
            reader.load()
            assertTrue(reader.state.until { !it.loading }.gone)
        }

    @Test
    fun `TV-27 a vote is sent, changed and shown as the server answers`() =
        runBlocking {
            val id = longTopic(3)
            val asked = fake.messages(id)[1]
            val poll =
                Poll(
                    "p_1",
                    "Which first?",
                    options = listOf(PollOption("o_1", "Sungold"), PollOption("o_2", "Brandywine")),
                )
            fake.attachPoll(asked.id, poll)
            val reader = TopicReader(signedIn(), id, scope)
            reader.load(asked.id)
            reader.state.until { !it.loading }

            reader.vote(asked.id, "p_1", listOf("o_2"))
            val voted =
                reader.state.until {
                    it.voting.isEmpty() && pollOf(
                        it,
                        asked.id,
                    )?.total == 1
                }
            assertEquals(listOf("o_2"), pollOf(voted, asked.id)?.myVotes)
            assertEquals(listOf(0, 1), pollOf(voted, asked.id)?.options?.map { it.count })

            reader.vote(asked.id, "p_1", listOf("o_1"))
            reader.state.until { pollOf(it, asked.id)?.myVotes == listOf("o_1") }
            assertEquals(listOf("o_1"), fake.votesOf(ada, "p_1"))
        }

    @Test
    fun `TV-02 a quote leads to its message, here or in the topic it is in now`() =
        runBlocking {
            val id = longTopic(60)
            val other = longTopic(2)
            val reader = TopicReader(signedIn(), id, scope)
            reader.load()
            reader.state.until { !it.loading }
            val shown =
                reader.state.value.messages
                    .last()

            assertEquals(id, reader.reach(shown.id))

            val far = fake.messages(id)[3]
            assertEquals(id, reader.reach(far.id))
            val around = reader.state.until { !it.loading && it.landing == far.id }
            assertTrue(around.messages.any { it.id == far.id })

            assertEquals(other, reader.reach(fake.messages(other)[1].id))
            assertNull(reader.reach("m_missing"))
        }

    private fun pollOf(
        state: TopicState,
        messageId: String,
    ) = state.messages.firstOrNull { it.id == messageId }?.poll
}

/** Time for a message's later events to arrive in a test that checks none counts twice. */
private const val SETTLE = 500L
