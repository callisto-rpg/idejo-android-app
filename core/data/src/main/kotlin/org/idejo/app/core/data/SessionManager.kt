// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.OAuthClient
import org.idejo.app.core.model.Server
import org.idejo.app.core.model.TokenGrant

/** What the app shows first. */
sealed interface Launch {
    data object Loading : Launch

    /** No forum yet: the welcome screen (MD-01). */
    data object Welcome : Launch

    data class Forum(
        val session: ForumSession,
    ) : Launch
}

/**
 * The forums the app holds and the one open. Single-forum mode (MD-03, M1): one forum; adding
 * another replaces it, signing the old one out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionManager(
    private val store: ForumStore,
    private val vault: TokenVault,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    socketDispatcher: CoroutineDispatcher,
    /** For tests and the fake server: an http address kept as it is. */
    private val addressOf: (String) -> ForumAddress = ::httpsAddress,
) {
    private val socketScope =
        CoroutineScope(SupervisorJob() + socketDispatcher.limitedParallelism(1))
    private val oauth = OAuthClient(http)
    private val lock = Mutex()
    private var session: ForumSession? = null

    private val host =
        object : SessionHost {
            override suspend fun signedOut(session: ForumSession) = markSignedOut(session)

            override suspend fun serverRead(server: Server) = keepTheme(server)
        }

    private val mutableLaunch = MutableStateFlow<Launch>(Launch.Loading)
    val launch: StateFlow<Launch> = mutableLaunch.asStateFlow()

    /** The forum open, if any. */
    val active: ForumSession? get() = (mutableLaunch.value as? Launch.Forum)?.session

    /** Reads the app's state and opens the forum it had open (MD-05). */
    suspend fun restore() =
        lock.withLock {
            val state = store.current()
            open(state.forum(state.active) ?: state.forums.firstOrNull())
        }

    /**
     * Adds the forum [found] by discovery, signed in with [grant] or as a visitor (SI-05),
     * and opens it. In single-forum mode it takes the place of any other, signed out.
     */
    suspend fun add(
        found: DiscoveryResult.Found,
        grant: TokenGrant? = null,
    ) = lock.withLock {
        val origin = found.address.toString()
        val state = store.current()
        state.forums.filter { it.origin != origin }.forEach { revoke(it) }
        val kept = state.forum(origin)
        val record =
            (kept ?: ForumRecord(origin, found.discovery)).copy(
                discovery = found.discovery,
                sealedToken = grant?.let { vault.seal(it.accessToken) },
                tokenId = grant?.tokenId,
                userId = grant?.user?.id,
                signedOut = false,
            )
        store.update { AppState(forums = listOf(record), active = origin) }
        open(record)
    }

    /** Signed in again after SI-09, or from a visitor (SI-05). */
    suspend fun signedIn(
        origin: String,
        grant: TokenGrant,
    ) = lock.withLock {
        val state =
            store.updateForum(origin) {
                it.copy(
                    sealedToken = vault.seal(grant.accessToken),
                    tokenId = grant.tokenId,
                    userId = grant.user?.id,
                    signedOut = false,
                )
            }
        open(state.forum(origin))
    }

    /**
     * Signs out of the forum (SI-11): the token revoked on the server, forgotten here; the
     * forum stays, as a visitor. Its cache, drafts and push registration come with M5 and M3.
     */
    suspend fun signOut(origin: String) =
        lock.withLock {
            val state = store.current()
            state.forum(origin)?.let { revoke(it) }
            open(
                store
                    .updateForum(origin) {
                        it.copy(sealedToken = null, tokenId = null, signedOut = false)
                    }.forum(origin),
            )
        }

    /** Signs out and removes the forum: back to the welcome screen (single-forum mode). */
    suspend fun remove(origin: String) =
        lock.withLock {
            store.current().forum(origin)?.let { revoke(it) }
            store.update { state ->
                AppState(forums = state.forums.filterNot { it.origin == origin })
            }
            open(null)
        }

    /** Changes the open forum's kept choices (theme mode, view, folds…). */
    suspend fun updateForum(transform: (ForumRecord) -> ForumRecord) {
        val current = active ?: return
        val record = store.updateForum(current.origin, transform).forum(current.origin) ?: return
        current.updated(record)
    }

    private suspend fun revoke(record: ForumRecord) {
        val token = vault.open(record.sealedToken) ?: return
        val revoke = record.discovery.oauth?.revoke ?: return
        oauth.revoke(addressOf(record.origin), revoke, token)
    }

    private fun open(record: ForumRecord?) {
        session?.close()
        session = null
        if (record == null) {
            mutableLaunch.value = Launch.Welcome
            return
        }
        val token = vault.open(record.sealedToken)
        val opened =
            ForumSession(
                address = addressOf(record.origin),
                record = record,
                token = token,
                http = http,
                scope = socketScope,
                host = host,
            )
        session = opened
        opened.start()
        mutableLaunch.value = Launch.Forum(opened)
    }

    /** SI-09: the token is dropped; the forum shows "You were signed out" until signed in. */
    private suspend fun markSignedOut(session: ForumSession) {
        val record =
            store.updateForum(
                session.origin,
            ) { it.copy(sealedToken = null, tokenId = null, signedOut = true) }
        record.forum(session.origin)?.let(session::updated)
    }

    private suspend fun keepTheme(server: Server) {
        val current = active ?: return
        if (current.record.value.theme == server.theme) return
        scope.launch { updateForum { it.copy(theme = server.theme) } }
    }
}

private fun httpsAddress(origin: String): ForumAddress =
    (ForumAddress.parse(origin) as? ForumAddress.Parsed.Valid)?.address
        ?: ForumAddress.forLocalTesting(origin.toHttpUrl())
