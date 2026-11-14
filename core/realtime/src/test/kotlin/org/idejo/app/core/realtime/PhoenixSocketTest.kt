// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.realtime

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PhoenixSocketTest {
    private val transport = FakeTransport()

    private fun TestScope.connected(): PhoenixSocket {
        val socket = PhoenixSocket(transport, backgroundScope)
        socket.connect()
        runCurrent()
        transport.last.open()
        runCurrent()
        assertEquals(SocketState.Connected, socket.state.value)
        return socket
    }

    /** Joins [channel], answering its join with [response]. */
    private fun TestScope.joined(
        channel: PhoenixChannel,
        response: String = "{}",
    ) {
        channel.join()
        runCurrent()
        transport.last.reply(
            transport.last.sent(Phoenix.JOIN).last {
                it.topic == channel.topic
            },
            response = response,
        )
        runCurrent()
    }

    @Test
    fun `frames are protocol 2 arrays, anything else ignored`() {
        val frame =
            Frame(
                "1",
                "2",
                "topic:t_1",
                "phx_join",
                JsonObject(
                    mapOf("a" to JsonPrimitive(1)),
                ),
            )
        assertEquals("""["1","2","topic:t_1","phx_join",{"a":1}]""", frame.encode())
        assertEquals(frame, Frame.decode(frame.encode()))
        assertEquals(
            Frame(null, "3", "phoenix", "heartbeat"),
            Frame.decode("""[null,"3","phoenix","heartbeat",{}]"""),
        )
        assertNull(Frame.decode("""{"topic": "x"}"""))
        assertNull(Frame.decode("""[null, null, "x"]"""))
        assertNull(Frame.decode("not json"))
    }

    @Test
    fun `a heartbeat every 30 seconds, and a reconnect when one goes unanswered`() =
        runTest {
            val socket = connected()
            advanceTimeBy(30.seconds)
            runCurrent()
            val beat = transport.last.sent(Phoenix.HEARTBEAT).single()
            assertNull(beat.joinRef)
            assertEquals(Phoenix.TOPIC, beat.topic)
            transport.last.reply(beat)
            runCurrent()

            advanceTimeBy(30.seconds)
            runCurrent()
            assertEquals(2, transport.last.sent(Phoenix.HEARTBEAT).size)
            // Unanswered: at the next beat the connection is given up.
            advanceTimeBy(30.seconds)
            runCurrent()
            assertTrue(transport.last.closed)
            assertEquals(SocketState.Waiting(1, 1.seconds), socket.state.value)
            advanceTimeBy(1.seconds)
            runCurrent()
            assertEquals(2, transport.connections.size)
        }

    @Test
    fun `reconnects with backoff 1, 2, 5, 10, then 30 seconds`() =
        runTest {
            val socket = connected()
            val waits = mutableListOf<Int>()
            repeat(6) {
                transport.last.drop()
                runCurrent()
                val waiting = socket.state.value as SocketState.Waiting
                waits += waiting.delay.inWholeSeconds.toInt()
                advanceTimeBy(waiting.delay)
                runCurrent()
                assertEquals(SocketState.Connecting, socket.state.value)
            }
            assertEquals(listOf(1, 2, 5, 10, 30, 30), waits)
            transport.last.open()
            runCurrent()
            transport.last.drop()
            runCurrent()
            assertEquals(SocketState.Waiting(1, 1.seconds), socket.state.value)
        }

    @Test
    fun `SI-09 a refused upgrade is not tried again`() =
        runTest {
            val socket = PhoenixSocket(transport, backgroundScope)
            socket.connect()
            runCurrent()
            transport.last.listener.onFailure(java.io.IOException("403"), 403)
            runCurrent()
            assertEquals(SocketState.Refused(Refusal.SignedOut), socket.state.value)
            advanceTimeBy(60.seconds)
            runCurrent()
            assertEquals(1, transport.connections.size)

            socket.connect()
            runCurrent()
            transport.last.listener.onFailure(java.io.IOException("404"), 404)
            runCurrent()
            assertEquals(SocketState.Refused(Refusal.ApiOff), socket.state.value)
        }

    @Test
    fun `SI-05 no token, no socket`() =
        runTest {
            transport.hasToken = false
            val socket = PhoenixSocket(transport, backgroundScope)
            socket.connect()
            runCurrent()
            assertEquals(SocketState.Refused(Refusal.NoToken), socket.state.value)
        }

    @Test
    fun `NF-04 disconnect closes and stays closed`() =
        runTest {
            val socket = connected()
            socket.disconnect()
            runCurrent()
            assertTrue(transport.last.closed)
            assertEquals(SocketState.Disconnected, socket.state.value)
            advanceTimeBy(120.seconds)
            runCurrent()
            assertEquals(1, transport.connections.size)
            assertTrue(transport.last.sent(Phoenix.HEARTBEAT).isEmpty())
        }

    @Test
    fun `a channel joins, is joined again after a reconnect, and leaves`() =
        runTest {
            val socket = connected()
            val channel = socket.channel("topic:t_1")
            joined(channel, """{"cursor": 7}""")
            assertEquals(ChannelState.Joined(json("""{"cursor": 7}""")), channel.state.value)
            val join = transport.last.sent(Phoenix.JOIN).single()
            assertEquals(join.ref, join.joinRef)

            transport.last.drop()
            runCurrent()
            assertEquals(ChannelState.Errored, channel.state.value)
            advanceTimeBy(1.seconds)
            runCurrent()
            transport.last.open()
            runCurrent()
            val rejoin = transport.last.sent(Phoenix.JOIN).single()
            transport.last.reply(rejoin)
            runCurrent()
            assertTrue(channel.state.value is ChannelState.Joined)

            channel.leave()
            runCurrent()
            assertEquals(
                rejoin.ref,
                transport.last
                    .sent(Phoenix.LEAVE)
                    .single()
                    .joinRef,
            )
            assertEquals(ChannelState.Left, channel.state.value)
        }

    @Test
    fun `a channel joined while disconnected joins when the socket opens`() =
        runTest {
            val socket = PhoenixSocket(transport, backgroundScope)
            val channel = socket.channel("user:me")
            channel.join()
            socket.connect()
            runCurrent()
            assertEquals(ChannelState.Joining, channel.state.value)
            transport.last.open()
            runCurrent()
            assertEquals(
                "user:me",
                transport.last
                    .sent(Phoenix.JOIN)
                    .single()
                    .topic,
            )
        }

    @Test
    fun `a refused join is not tried again`() =
        runTest {
            val socket = connected()
            val channel = socket.channel("topic:hidden")
            channel.join()
            runCurrent()
            transport.last.reply(
                transport.last.sent(Phoenix.JOIN).single(),
                "error",
                """{"reason": "not_found"}""",
            )
            runCurrent()
            assertEquals(ChannelState.Refused("not_found"), channel.state.value)

            transport.last.drop()
            runCurrent()
            advanceTimeBy(1.seconds)
            runCurrent()
            transport.last.open()
            runCurrent()
            assertTrue(transport.last.sent(Phoenix.JOIN).isEmpty())
        }

    @Test
    fun `an unanswered join or a server error joins again with backoff`() =
        runTest {
            val socket = connected()
            val channel = socket.channel("topic:t_1")
            channel.join()
            runCurrent()
            advanceTimeBy(10.seconds)
            runCurrent()
            assertEquals(ChannelState.Errored, channel.state.value)
            advanceTimeBy(1.seconds)
            runCurrent()
            val second = transport.last.sent(Phoenix.JOIN).last()
            transport.last.reply(second)
            runCurrent()
            assertTrue(channel.state.value is ChannelState.Joined)

            transport.last.push("topic:t_1", Phoenix.ERROR, "{}")
            runCurrent()
            assertEquals(ChannelState.Errored, channel.state.value)
            advanceTimeBy(1.seconds)
            runCurrent()
            assertEquals(3, transport.last.sent(Phoenix.JOIN).size)
        }

    @Test
    fun `pushes get their reply, or say why not`() =
        runTest {
            val socket = connected()
            val channel = socket.channel("topic:t_1")
            assertEquals(Reply.NotJoined, channel.push("typing"))
            joined(channel)

            val typing = async { channel.push("typing") }
            runCurrent()
            val sent = transport.last.sent("typing").single()
            assertEquals(
                transport.last
                    .sent(Phoenix.JOIN)
                    .single()
                    .ref,
                sent.joinRef,
            )
            transport.last.reply(sent, "error", """{"reason": "too_soon"}""")
            runCurrent()
            assertEquals("too_soon", (typing.await() as Reply.Error).reason)

            val unanswered = async { channel.push("typing") }
            advanceTimeBy(11.seconds)
            assertEquals(Reply.Timeout, unanswered.await())

            val dropped = async { channel.push("typing") }
            runCurrent()
            transport.last.drop()
            runCurrent()
            assertEquals(Reply.Disconnected, dropped.await())
        }

    @Test
    fun `events from an earlier join are ignored`() =
        runTest {
            val socket = connected()
            val channel = socket.channel("topic:t_1")
            channel.messages.test {
                joined(channel)
                assertTrue(awaitItem() is ChannelMessage.Joined)
                transport.last.push(
                    "topic:t_1",
                    "messages.moved",
                    """{"topic_id": "t_1"}""",
                    joinRef = "old",
                )
                transport.last.push("topic:t_1", "messages.moved", """{"topic_id": "t_1"}""")
                runCurrent()
                assertEquals(
                    ChannelMessage.Event("messages.moved", json("""{"topic_id": "t_1"}""")),
                    awaitItem(),
                )
                expectNoEvents()
            }
        }

    @Test
    fun `catch-up after a rejoin asks from the last cursor seen`() =
        runTest {
            val socket = connected()
            val stream = ForumEvents(socket).topic("t_1")
            stream.events.test {
                stream.join()
                runCurrent()
                transport.last.reply(
                    transport.last.sent(Phoenix.JOIN).single(),
                    response = """{"cursor": 40}""",
                )
                runCurrent()
                assertEquals(40L, stream.cursor.value)

                transport.last.push(
                    "topic:t_1",
                    "message.appended",
                    """{"message": $MESSAGE, "cursor": 42}""",
                )
                runCurrent()
                val appended = awaitItem() as IdejoEvent.MessageChanged
                assertEquals(IdejoEvent.Change.Appended, appended.change)
                assertEquals(42L, stream.cursor.value)

                transport.last.drop()
                runCurrent()
                advanceTimeBy(1.seconds)
                runCurrent()
                transport.last.open()
                runCurrent()
                transport.last.reply(
                    transport.last.sent(Phoenix.JOIN).single(),
                    response = """{"cursor": 50}""",
                )
                runCurrent()
                assertEquals(IdejoEvent.CatchUp(since = 42), awaitItem())
                stream.caughtUp(50)
                assertEquals(50L, stream.cursor.value)
            }
        }

    private companion object {
        const val MESSAGE =
            """{"id": "m_1", "topic_id": "t_1", "author": {"id": "u_ada", "display_name": "Ada"},
               "state": "open", "body_md": "Hi. Hello", "cursor": 42}"""
    }
}
