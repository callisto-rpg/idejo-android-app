// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.testing

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.ForumApi
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.model.AccessToken
import org.idejo.app.core.model.MessageState
import org.idejo.app.core.realtime.ChannelState
import org.idejo.app.core.realtime.ForumEvents
import org.idejo.app.core.realtime.IdejoEvent
import org.idejo.app.core.realtime.OkHttpTransport
import org.idejo.app.core.realtime.PhoenixSocket
import org.idejo.app.core.realtime.Refusal
import org.idejo.app.core.realtime.SocketOptions
import org.idejo.app.core.realtime.SocketState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The API guide's section 7, over a real WebSocket to the fake forum. */
@OptIn(ExperimentalCoroutinesApi::class)
class GuideEventsTest {
    private val fake = FakeIdejo()
    private val http = IdejoHttp.forLocalTesting()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private var token: AccessToken? = fake.tokenFor(FakePeople.Ada)
    private val socket =
        PhoenixSocket(
            OkHttpTransport(fake.address, "/api/v1/socket", { token }, http),
            scope,
            SocketOptions(backoff = { 50.milliseconds }),
        )
    private val events = ForumEvents(socket)

    @After
    fun stop() {
        scope.cancel()
        fake.close()
    }

    private suspend fun connected() {
        socket.connect()
        withTimeout(5.seconds) { socket.state.first { it == SocketState.Connected } }
    }

    @Test
    fun `message events reach a joined topic, with their cursors`() =
        runBlocking {
            connected()
            val topic = events.topic("t_1")
            topic.events.test(timeout = 5.seconds) {
                topic.join()
                withTimeout(5.seconds) { topic.state.first { it is ChannelState.Joined } }
                assertEquals(1L, topic.cursor.value)

                fake.post(FakePeople.Mina, "t_1", "Sungold every year")
                val opened = awaitItem() as IdejoEvent.MessageChanged
                assertEquals(IdejoEvent.Change.Opened, opened.change)
                assertEquals("Mina Kader", opened.message.author.displayName)
                val closed = awaitItem() as IdejoEvent.MessageChanged
                assertEquals(IdejoEvent.Change.Closed, closed.change)
                assertEquals(MessageState.Closed, closed.message.state)
                assertEquals(closed.cursor, topic.cursor.value)
            }
        }

    @Test
    fun `a hidden or missing topic refuses the join`() =
        runBlocking {
            connected()
            val topic = events.topic("t_missing")
            topic.join()
            assertEquals(
                ChannelState.Refused("not_found"),
                withTimeout(5.seconds) {
                    topic.state.first { it is ChannelState.Refused }
                },
            )
        }

    @Test
    fun `after a disconnect the topic is joined again and caught up`() =
        runBlocking {
            connected()
            val api = ForumApi(fake.address, "/api/v1", { token }, http)
            val topic = events.topic("t_1")
            topic.events.test(timeout = 5.seconds) {
                topic.join()
                withTimeout(5.seconds) { topic.state.first { it is ChannelState.Joined } }

                socket.disconnect()
                withTimeout(5.seconds) { socket.state.first { it == SocketState.Disconnected } }
                fake.post(FakePeople.Leo, "t_1", "Missed this one")
                socket.connect()

                val catchUp = awaitItem() as IdejoEvent.CatchUp
                assertEquals(1L, catchUp.since)
                val changes =
                    (
                        api.changes(
                            "t_1",
                            requireNotNull(catchUp.since),
                        ) as ApiResult.Success
                    ).value
                assertEquals(listOf("Missed this one."), changes.data.map { it.bodyMd })
                topic.caughtUp(changes.cursor)
                assertEquals(changes.cursor, topic.cursor.value)
            }
        }

    @Test
    fun `SI-09 a revoked token hears token_revoked, and the socket is refused after`() =
        runBlocking {
            connected()
            val user = events.user()
            user.events.test(timeout = 5.seconds) {
                user.join()
                withTimeout(5.seconds) { user.state.first { it is ChannelState.Joined } }
                fake.revoke(requireNotNull(token))
                assertTrue(awaitItem() is IdejoEvent.TokenRevoked)
            }
            assertEquals(
                SocketState.Refused(Refusal.SignedOut),
                withTimeout(5.seconds) { socket.state.first { it is SocketState.Refused } },
            )
        }

    @Test
    fun `the API turned off refuses the socket`() =
        runBlocking {
            fake.apiOff = true
            socket.connect()
            assertEquals(
                SocketState.Refused(Refusal.ApiOff),
                withTimeout(5.seconds) { socket.state.first { it is SocketState.Refused } },
            )
        }
}
