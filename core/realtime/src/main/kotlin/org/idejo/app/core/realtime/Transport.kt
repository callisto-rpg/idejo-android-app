// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.realtime

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.encodeUtf8
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.TokenSource

/** A WebSocket, as the socket needs it: so tests can stand in for the network. */
interface SocketTransport {
    /** Opens a connection, or returns null when there is nothing to connect with (no token). */
    fun connect(listener: TransportListener): TransportConnection?
}

interface TransportConnection {
    fun send(text: String): Boolean

    fun close(
        code: Int,
        reason: String,
    )
}

/** Called from any thread. */
interface TransportListener {
    fun onOpen()

    fun onMessage(text: String)

    fun onClosed(code: Int)

    /** [status] is the refused upgrade's HTTP status (403 bad token, 404 API off), if any. */
    fun onFailure(
        cause: Throwable,
        status: Int?,
    )
}

/**
 * The real WebSocket to `wss://<forum>/api/v1/socket/websocket?vsn=2.0.0`. The token goes
 * in `Sec-WebSocket-Protocol` as Phoenix's `auth_token` (steward decision, 2026-11-14),
 * never in the address (SI-04): `phoenix, base64url.bearer.phx.<the token, base64url>`.
 */
class OkHttpTransport(
    private val address: ForumAddress,
    private val socketPath: String,
    private val tokens: TokenSource,
    private val http: OkHttpClient,
) : SocketTransport {
    override fun connect(listener: TransportListener): TransportConnection? {
        val token = tokens.token() ?: return null
        val url =
            address
                .resolve(socketPath.trimEnd('/') + "/websocket")
                ?.newBuilder()
                ?.addQueryParameter("vsn", PROTOCOL_VERSION)
                ?.build() ?: return null
        val request =
            Request
                .Builder()
                .url(url)
                .header("Sec-WebSocket-Protocol", protocols(token.value))
                .build()
        val socket = http.newWebSocket(request, Listener(listener))
        return object : TransportConnection {
            override fun send(text: String) = socket.send(text)

            override fun close(
                code: Int,
                reason: String,
            ) {
                socket.close(code, reason)
            }
        }
    }

    private class Listener(
        private val listener: TransportListener,
    ) : WebSocketListener() {
        override fun onOpen(
            webSocket: WebSocket,
            response: Response,
        ) = listener.onOpen()

        override fun onMessage(
            webSocket: WebSocket,
            text: String,
        ) = listener.onMessage(text)

        override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            webSocket.close(code, null)
        }

        override fun onClosed(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) = listener.onClosed(code)

        override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?,
        ) = listener.onFailure(t, response?.code)
    }

    companion object {
        const val PROTOCOL_VERSION = "2.0.0"
        const val AUTH_TOKEN_PREFIX = "base64url.bearer.phx."

        internal fun protocols(token: String): String =
            "phoenix, " + AUTH_TOKEN_PREFIX + token.encodeUtf8().base64Url().trimEnd('=')
    }
}
