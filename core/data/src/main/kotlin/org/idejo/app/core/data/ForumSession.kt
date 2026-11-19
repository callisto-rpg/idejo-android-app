// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import okhttp3.OkHttpClient
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.ForumApi
import org.idejo.app.core.api.IdejoJson
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

/** The forum did not answer, or answered with a server error: the saved copy will do. */
val ApiProblem.unreachable: Boolean
    get() =
        this is ApiProblem.Network ||
            (this is ApiProblem.Server && status >= HTTP_SERVER_ERROR) ||
            (this is ApiProblem.Malformed && status >= HTTP_SERVER_ERROR)

private const val HTTP_SERVER_ERROR = 500

/** What a session tells the SessionManager, which keeps the forum on disk. */
internal interface SessionHost {
    /** SI-09, SI-12: the token was refused or revoked. */
    suspend fun signedOut(session: ForumSession)

    /** `GET /server` was read: its theme is kept (TH-06). */
    suspend fun serverRead(server: Server)

    /** The forums' last answers, for offline (OF-01); none in most tests. */
    val cache: PageCache? get() = null
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

    private val mutableUserEvents = MutableSharedFlow<IdejoEvent>(extraBufferCapacity = EVENTS)

    /**
     * The `user:me` channel's events while signed in (`unread.changed`, notifications…). The
     * session joins it once; screens listen here instead of joining it again.
     */
    val userEvents: SharedFlow<IdejoEvent> = mutableUserEvents.asSharedFlow()

    /** The signed-in member (`GET /me`), one copy for every screen of this forum (ST-02). */
    val me: MeRepository = MeRepository(this)

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
        when (result) {
            is ApiResult.Success -> mutableOffline.value = false
            is ApiResult.Failure -> if (result.problem.unreachable) mutableOffline.value = true
        }
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

    private val mutableOffline = MutableStateFlow(false)

    /**
     * The forum did not answer the last call (OF-05): reactions, follows and votes are not
     * queued, so their buttons wait, disabled, until any call is answered again.
     */
    val offline: StateFlow<Boolean> = mutableOffline.asStateFlow()

    private val mutableSavedCopy = MutableStateFlow<Long?>(null)

    /**
     * When the oldest saved copy on screen was read, while the forum cannot be reached
     * (OF-02): "Offline — showing saved copy from {time}". Null once the forum answers.
     */
    val savedCopy: StateFlow<Long?> = mutableSavedCopy.asStateFlow()

    /**
     * [request] as [call], its answer kept under [key] (OF-01). When the forum cannot be
     * reached (no answer, or a server error), the copy kept is the answer, and [savedCopy]
     * says from when.
     */
    suspend fun <T> cached(
        key: String,
        serializer: KSerializer<T>,
        request: suspend ForumApi.() -> ApiResult<T>,
    ): ApiResult<T> {
        val result = call(request)
        val cache = host.cache ?: return result
        when (result) {
            is ApiResult.Success -> {
                mutableSavedCopy.value = null
                runCatching { IdejoJson.encodeToString(serializer, result.value) }
                    .onSuccess { cache.put(origin, key, it) }
            }

            is ApiResult.Failure -> {
                if (!result.problem.unreachable) return result
                val page = cache.get(origin, key) ?: return result
                val value =
                    runCatching { IdejoJson.decodeFromString(serializer, page.json) }.getOrNull()
                        ?: return result
                mutableSavedCopy.update { minOf(it ?: page.savedAt, page.savedAt) }
                return ApiResult.Success(value)
            }
        }
        return result
    }

    /** [cached] with the serializer of [T]. */
    suspend inline fun <reified T> cached(
        key: String,
        noinline request: suspend ForumApi.() -> ApiResult<T>,
    ): ApiResult<T> = cached(key, serializer<T>(), request)

    /** Reads `GET /server`, keeping its theme for the next launch (TH-06). */
    suspend fun refreshServer(): ApiResult<Server> =
        cached("server") { server() }.also { result ->
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
                mutableUserEvents.emit(event)
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

    internal companion object {
        const val DEFAULT_BASE = "/api/v1"
        internal const val DEFAULT_SOCKET = "/api/v1/socket"
        private const val EVENTS = 64
    }
}
