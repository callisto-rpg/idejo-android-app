// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.ForumApi
import org.idejo.app.core.model.AccessToken
import org.idejo.app.core.model.Server
import org.idejo.app.core.realtime.ForumEvents
import org.idejo.app.core.realtime.IdejoEvent
import org.idejo.app.core.realtime.OkHttpTransport
import org.idejo.app.core.realtime.PhoenixSocket
import java.time.Instant

/** Who the app is on a forum. */
sealed interface Access {
    /** No token: public categories only (SI-05). */
    data object Visitor : Access

    data object SignedIn : Access

    /** The token was refused or revoked (SI-09, SI-12): "You were signed out of {name}". */
    data object SignedOut : Access

    /** `403 banned` / `403 suspended` (SI-10): the server's message; writing disabled. */
    data class Barred(
        val message: String,
        val until: Instant?,
    ) : Access
}

/** What a session tells the SessionManager, which keeps the forum on disk. */
internal interface SessionHost {
    /** SI-09, SI-12: the token was refused or revoked. */
    suspend fun signedOut(session: ForumSession)

    /** `GET /server` was read: its theme is kept (TH-06). */
    suspend fun serverRead(server: Server)
}

/**
 * One forum while the app holds it (CLAUDE.md → Architecture): its client with its token,
 * its socket, its server. Every call goes through [call], which notices a refused token
 * (SI-09) or a barred account (SI-10) whatever screen made it.
 */
class ForumSession internal constructor(
    val address: ForumAddress,
    record: ForumRecord,
    private val token: AccessToken?,
    http: OkHttpClient,
    private val scope: CoroutineScope,
    private val host: SessionHost,
) {
    private val mutableRecord = MutableStateFlow(record)

    /** The forum as kept on the device: name, theme, choices. */
    val record: StateFlow<ForumRecord> = mutableRecord.asStateFlow()

    private val mutableAccess =
        MutableStateFlow(
            when {
                token != null -> Access.SignedIn
                record.signedOut -> Access.SignedOut
                else -> Access.Visitor
            },
        )
    val access: StateFlow<Access> = mutableAccess.asStateFlow()

    private val mutableServer = MutableStateFlow<Server?>(null)
    val server: StateFlow<Server?> = mutableServer.asStateFlow()

    val api: ForumApi =
        ForumApi(address, record.discovery.api?.base ?: DEFAULT_BASE, { currentToken() }, http)

    /** The socket: connected while the app is in the foreground and signed in (NF-04). */
    val socket: PhoenixSocket =
        PhoenixSocket(
            OkHttpTransport(
                address,
                record.discovery.api?.socket ?: DEFAULT_SOCKET,
                { currentToken() },
                http,
            ),
            scope,
        )
    val events: ForumEvents = ForumEvents(socket)

    val name: String get() = mutableRecord.value.name
    val origin: String get() = mutableRecord.value.origin

    private fun currentToken(): AccessToken? =
        token.takeIf {
            mutableAccess.value !=
                Access.SignedOut
        }

    /** [request] on this forum's API, watching for SI-09, SI-10 and SI-12. */
    suspend fun <T> call(request: suspend ForumApi.() -> ApiResult<T>): ApiResult<T> {
        val result = api.request()
        val problem =
            ((result as? ApiResult.Failure)?.problem as? ApiProblem.Server) ?: return result
        when {
            problem.signedOut && token != null -> {
                signedOut()
            }

            problem.barred -> {
                mutableAccess.value =
                    Access.Barred(problem.error.message, problem.error.until)
            }
        }
        return result
    }

    /** Reads `GET /server`, keeping its theme for the next launch (TH-06). */
    suspend fun refreshServer(): ApiResult<Server> =
        call { server() }.also { result ->
            (result as? ApiResult.Success)?.value?.let {
                mutableServer.value = it
                host.serverRead(it)
            }
        }

    /** Hears `token.revoked` and `settings.changed` on user:me while signed in. */
    internal fun start() {
        if (token == null) return
        val user = events.user()
        scope.launch {
            user.events.collect { event ->
                when (event) {
                    is IdejoEvent.TokenRevoked -> signedOut()
                    is IdejoEvent.SettingsChanged -> refreshServer()
                    else -> Unit
                }
            }
        }
        user.join()
    }

    internal fun updated(record: ForumRecord) {
        mutableRecord.value = record
    }

    internal fun close() = socket.disconnect()

    private suspend fun signedOut() {
        if (mutableAccess.value == Access.SignedOut) return
        mutableAccess.value = Access.SignedOut
        socket.disconnect()
        host.signedOut(this)
    }

    private companion object {
        const val DEFAULT_BASE = "/api/v1"
        const val DEFAULT_SOCKET = "/api/v1/socket"
    }
}
