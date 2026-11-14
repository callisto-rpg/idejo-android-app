// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.realtime

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.decodeBase64
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.model.AccessToken
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OkHttpTransportTest {
    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun testAddress(): ForumAddress = ForumAddress.forLocalTesting(server.url("/"))

    @Test
    fun `SI-04 the token goes as Phoenix's auth_token, never in the address`() {
        val opened = CountDownLatch(1)
        val closed = CountDownLatch(1)
        server.enqueue(
            MockResponse
                .Builder()
                .webSocketUpgrade(
                    object : WebSocketListener() {
                        override fun onOpen(
                            webSocket: WebSocket,
                            response: Response,
                        ) {
                            opened.countDown()
                        }

                        // Answers the client's close, so the server can shut down at once.
                        override fun onClosing(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) {
                            webSocket.close(code, null)
                            closed.countDown()
                        }
                    },
                ).build(),
        )
        val connection =
            OkHttpTransport(
                testAddress(),
                "/api/v1/socket",
                { AccessToken("idj_~~~?") },
                OkHttpClient(),
            ).connect(Recorder())
        assertTrue(opened.await(5, TimeUnit.SECONDS))
        val request = server.takeRequest()
        assertEquals("/api/v1/socket/websocket", request.url.encodedPath)
        assertEquals("vsn=2.0.0", request.url.encodedQuery)
        assertFalse(request.url.toString().contains("idj_"))
        assertNull(request.headers["Authorization"])
        val protocols = requireNotNull(request.headers["Sec-WebSocket-Protocol"]).split(", ")
        assertEquals("phoenix", protocols[0])
        val encoded = protocols[1].removePrefix(OkHttpTransport.AUTH_TOKEN_PREFIX)
        assertTrue(protocols[1].startsWith(OkHttpTransport.AUTH_TOKEN_PREFIX))
        // Standard Base64 without padding: what Phoenix's Base.decode64!(padding: false) reads.
        assertFalse(encoded.contains('='))
        assertFalse(encoded.contains('-') || encoded.contains('_'))
        assertEquals("idj_~~~?", encoded.decodeBase64()?.utf8())
        assertTrue(encoded.contains('+') || encoded.contains('/'))
        connection?.close(1000, "bye")
        assertTrue(closed.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun `a refused upgrade reports its status`() {
        server.enqueue(MockResponse.Builder().code(403).build())
        val recorder = Recorder()
        OkHttpTransport(testAddress(), "/api/v1/socket", {
            AccessToken("idj_x")
        }, OkHttpClient()).connect(recorder)
        assertTrue(recorder.failed.await(5, TimeUnit.SECONDS))
        assertEquals(403, recorder.status)
    }

    @Test
    fun `SI-05 no token, no connection`() {
        assertNull(
            OkHttpTransport(testAddress(), "/api/v1/socket", {
                null
            }, OkHttpClient()).connect(Recorder()),
        )
        assertEquals(0, server.requestCount)
    }

    private class Recorder : TransportListener {
        val failed = CountDownLatch(1)

        @Volatile var status: Int? = null

        override fun onOpen() = Unit

        override fun onMessage(text: String) = Unit

        override fun onClosed(code: Int) = Unit

        override fun onFailure(
            cause: Throwable,
            status: Int?,
        ) {
            this.status = status
            failed.countDown()
        }
    }
}
