// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    /** idejo ID mode with no forum open: the group's inbox (MD-02, SI-06). */
    data class Inbox(
        val session: ForumSession,
    ) : Launch
}

/**
 * The forums the app holds and the one open. Single-forum mode (MD-03): one forum; adding
 * another replaces it, signing the old one out. idejo ID mode (MD-02): the group's inbox,
 * signed in first (SI-06), and any number of forums, each connected with its own token; one
 * session is open at a time, the inbox's or a forum's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One per thing done to the forums held, each small.
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

    private val mutableSignedOut = MutableSharedFlow<String>(extraBufferCapacity = SIGNED_OUT)

    /**
     * Each forum signed out of, by origin: by the member (SI-11), refused (SI-09), replaced or
     * removed. Its push registration ends with it (NT-02).
     */
    val signedOut: SharedFlow<String> = mutableSignedOut.asSharedFlow()

    private val mutableLaunch = MutableStateFlow<Launch>(Launch.Loading)
    val launch: StateFlow<Launch> = mutableLaunch.asStateFlow()

    /** The forum open, if any. */
    val active: ForumSession? get() = (mutableLaunch.value as? Launch.Forum)?.session

    /** The inbox, while it is on screen. */
    val inbox: ForumSession? get() = (mutableLaunch.value as? Launch.Inbox)?.session

    /** What the app keeps, as it changes: the forums held, the mode (MD-02). */
    val state: Flow<AppState> get() = store.state

    /** Reads the app's state and opens the forum, or the inbox, it had open (MD-05). */
    suspend fun restore() =
        lock.withLock {
            val state = store.current()
            val inbox = state.inbox
            when {
                state.mode == AppMode.IdejoId && inbox != null -> {
                    state.forum(state.active)?.let { open(it) } ?: openInbox(inbox)
                }

                else -> {
                    open(state.forum(state.active) ?: state.forums.firstOrNull())
                }
            }
        }

    /**
     * SI-06: the member signed in to the group's inbox ([found], with [grant]): idejo ID
     * mode, the inbox on screen. Forums the app held stay, each with its own token.
     */
    suspend fun signedInToInbox(
        found: DiscoveryResult.Found,
        grant: TokenGrant,
    ) = lock.withLock {
        val origin = found.address.toString()
        val record = keep(store.current().inbox?.takeIf { it.origin == origin }, found, grant)
        store.update { it.copy(mode = AppMode.IdejoId, inbox = record, active = null) }
        openInbox(record)
    }

    /**
     * SI-06: a forum of the group connected with [grant] while the inbox stays on screen; a
     * forum held already gets the new token.
     */
    suspend fun connect(
        found: DiscoveryResult.Found,
        grant: TokenGrant,
    ) = lock.withLock {
        val origin = found.address.toString()
        val record = keep(store.current().forum(origin), found, grant)
        store.update { it.with(record) }
    }

    /** idejo ID mode: the forum at [origin], held by the app, opened (IB-02). */
    suspend fun enter(origin: String) =
        lock.withLock {
            val record = store.update { it.copy(active = origin) }.forum(origin) ?: return@withLock
            open(record)
        }

    /** Whether the app holds a token for the forum at [origin] (SI-06). */
    suspend fun connected(origin: String): Boolean =
        store.current().forum(origin)?.sealedToken != null

    /** idejo ID mode: back to the inbox from a forum (IB-08). */
    suspend fun openInbox() =
        lock.withLock {
            val inbox = store.update { it.copy(active = null) }.inbox ?: return@withLock
            openInbox(inbox)
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
        val single = state.mode == AppMode.Single
        if (single) {
            state.forums.filter { it.origin != origin }.forEach {
                revoke(it)
                mutableSignedOut.tryEmit(it.origin)
            }
        }
        val record = keep(state.forum(origin), found, grant)
        store.update {
            if (single) {
                it.copy(forums = listOf(record), active = origin)
            } else {
                it.with(record).copy(active = origin)
            }
        }
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
        val inbox = state.inbox?.takeIf { it.origin == origin }
        if (inbox != null) openInbox(inbox) else open(state.forum(origin))
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
            // Once the new session is open, so what the registrar forgets reaches it (NT-02).
            mutableSignedOut.tryEmit(origin)
        }

    /**
     * Signs out and removes the forum: back to the welcome screen in single-forum mode, to
     * the inbox in idejo ID mode.
     */
    suspend fun remove(origin: String) =
        lock.withLock {
            store.current().forum(origin)?.let { revoke(it) }
            val state =
                store.update { state ->
                    state.copy(
                        forums = state.forums.filterNot { it.origin == origin },
                        active = null,
                    )
                }
            val inbox = state.inbox
            if (state.mode == AppMode.IdejoId && inbox != null) openInbox(inbox) else open(null)
            mutableSignedOut.tryEmit(origin)
        }

    /**
     * Changes what the app keeps of the forum at [origin], open or not (NT-02); by default
     * the open forum's kept choices (theme mode, view, folds…).
     */
    suspend fun updateForum(
        origin: String? = active?.origin,
        transform: (ForumRecord) -> ForumRecord,
    ) {
        origin ?: return
        val record = store.updateForum(origin, transform).record(origin) ?: return
        session?.takeIf { it.origin == origin }?.updated(record)
    }

    /** [kept] (or a new record) with [found]'s discovery and [grant]'s token, or none. */
    private fun keep(
        kept: ForumRecord?,
        found: DiscoveryResult.Found,
        grant: TokenGrant?,
    ): ForumRecord =
        (kept ?: ForumRecord(found.address.toString(), found.discovery)).copy(
            discovery = found.discovery,
            sealedToken = grant?.let { vault.seal(it.accessToken) },
            tokenId = grant?.tokenId,
            userId = grant?.user?.id,
            signedOut = false,
        )

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
        mutableLaunch.value = Launch.Forum(started(record))
    }

    private fun openInbox(record: ForumRecord) {
        session?.close()
        session = null
        mutableLaunch.value = Launch.Inbox(started(record))
    }

    private fun started(record: ForumRecord): ForumSession {
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
        return opened
    }

    /** SI-09: the token is dropped; the forum shows "You were signed out" until signed in. */
    private suspend fun markSignedOut(session: ForumSession) {
        val record =
            store.updateForum(
                session.origin,
            ) { it.copy(sealedToken = null, tokenId = null, signedOut = true) }
        record.record(session.origin)?.let(session::updated)
        mutableSignedOut.tryEmit(session.origin)
    }

    private suspend fun keepTheme(server: Server) {
        val current = session ?: return
        if (current.record.value.theme == server.theme) return
        scope.launch { updateForum(current.origin) { it.copy(theme = server.theme) } }
    }
}

private const val SIGNED_OUT = 8

private fun httpsAddress(origin: String): ForumAddress =
    (ForumAddress.parse(origin) as? ForumAddress.Parsed.Valid)?.address
        ?: ForumAddress.forLocalTesting(origin.toHttpUrl())
