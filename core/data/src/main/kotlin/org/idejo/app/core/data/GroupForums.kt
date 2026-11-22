// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.DirectoryClient
import org.idejo.app.core.api.DiscoveryClient
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.model.Community
import org.idejo.app.core.model.DirectoryForum
import org.idejo.app.core.model.DirectoryPage
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** One of the group's forums the member joined, and whether the app holds a token for it. */
data class GroupForum(
    val name: String,
    /** Its address as the inbox gives it: `https://garden.example.org/`. */
    val url: String,
    val logoUrl: String? = null,
    val connected: Boolean = false,
    /** Joined since the member last saw the group's forums (SI-07). */
    val new: Boolean = false,
)

/**
 * The group's forums in idejo ID mode (SI-06, SI-07): those the member joined, from the
 * inbox's `GET /me/communities` (API guide §9), read at every launch and once an hour; and
 * connecting them: each one's own sign-in page in a Custom Tab, one tap with the idejo ID
 * session, its token kept beside the inbox's.
 */
class GroupForums(
    private val sessions: SessionManager,
    private val signIn: SignInCoordinator,
    private val discovery: DiscoveryClient,
    private val directory: DirectoryClient,
    private val deviceName: String,
    /** A forum's address from the inbox's list; null for one the app may not reach (https). */
    private val addressOf: (String) -> ForumAddress? = ::httpsOnly,
) {
    private val mutableJoined = MutableStateFlow<List<GroupForum>>(emptyList())

    /** The forums joined as last read; empty outside idejo ID mode. */
    val joined: StateFlow<List<GroupForum>> = mutableJoined.asStateFlow()

    /** Forums joined since last seen and not connected: the logo's dot (SI-07). */
    val anyNew: Flow<Boolean> = joined.map { forums -> forums.any { it.new && !it.connected } }

    /**
     * Reads the list at once, then every [every] (SI-07), as long as [scope] lives: the
     * app's scope, from launch.
     */
    fun start(
        scope: CoroutineScope,
        every: Duration = 1.hours,
    ): Job =
        scope.launch {
            while (isActive) {
                refresh()
                delay(every)
            }
        }

    /** Reads the forums joined from the inbox, each marked connected or not, new or not. */
    suspend fun refresh(): ApiResult<List<GroupForum>> =
        when (val result = sessions.inboxCommunities()) {
            null -> {
                ApiResult.Failure(ApiProblem.Network(IOException("No idejo ID")))
            }

            is ApiResult.Success -> {
                val state = sessions.state.first()
                val connected = state.forums.filter { it.sealedToken != null }.map { it.origin }
                val forums =
                    result.value.mapNotNull { it.forum(connected.toSet(), state.seenForums) }
                mutableJoined.value = forums
                ApiResult.Success(forums)
            }

            is ApiResult.Failure -> {
                result
            }
        }

    /** Whether the group has a directory (SI-08): discovery's `community.directory_url`. */
    suspend fun hasDirectory(): Boolean = directoryUrl() != null

    /** **Find more forums** (SI-08): the directory's [page] for [query]. */
    suspend fun directory(
        query: String,
        page: Int,
    ): ApiResult<DirectoryPage> {
        val url =
            directoryUrl()
                ?: return ApiResult.Failure(ApiProblem.Network(IOException("No directory")))
        return directory.page(url, query, page)
    }

    /**
     * **Join** (SI-08): the forum's own sign-in page, as [connect]; signing in there with the
     * idejo ID makes the member one of its members. The list is read again after. Returns
     * the forum's origin once its token is kept, for the app to open it; else null.
     */
    suspend fun join(
        forum: DirectoryForum,
        openTab: suspend (HttpUrl) -> Boolean,
    ): String? {
        val joined = GroupForum(forum.name, forum.url, forum.logoUrl)
        return connect(joined, openTab).also { if (it != null) refresh() }
    }

    private suspend fun directoryUrl(): HttpUrl? =
        sessions.state
            .first()
            .inbox
            ?.discovery
            ?.community
            ?.directoryUrl
            ?.toHttpUrlOrNull()

    /** The member saw the group's forums (the inbox's list): none is new any more. */
    suspend fun seen() {
        val forums = mutableJoined.value
        sessions.seen(forums.map { it.url })
        mutableJoined.value = forums.map { it.copy(new = false) }
    }

    /** [forum] connected: marked so, without waiting for the next read. */
    private suspend fun marked(forum: GroupForum) {
        val connected = sessions.connected(forum.url)
        mutableJoined.value =
            mutableJoined.value.map {
                if (it.url == forum.url) it.copy(connected = it.connected || connected) else it
            }
    }

    /**
     * Connects [forum] (SI-06): its sign-in page is handed to [openTab]; its origin once the
     * token is kept, null when the member closed the page, refused, or the forum could not be
     * reached. The next forum waits for this one's end.
     */
    suspend fun connect(
        forum: GroupForum,
        openTab: suspend (HttpUrl) -> Boolean,
    ): String? {
        val address = addressOf(forum.url) ?: return null
        val found = discovery.discover(address) as? DiscoveryResult.Found ?: return null
        val origin = found.address.toString()
        return coroutineScope {
            // Listening before the page opens: its answer may come at once.
            val outcome =
                async(start = CoroutineStart.UNDISPATCHED) {
                    signIn.outcomes.first { it.origin == origin }
                }
            val url = signIn.startConnect(found, deviceName)
            if (url == null || !openTab(url)) {
                outcome.cancel()
                signIn.cancelIfWaiting()
                signIn.dismiss()
                return@coroutineScope null
            }
            val signedIn = outcome.await().signedIn
            // A failure shows as "Not connected" in the list, not as a dialog.
            signIn.dismiss()
            if (signedIn) marked(forum)
            origin.takeIf { signedIn }
        }
    }

    private fun Community.forum(
        connected: Set<String>,
        seen: Set<String>,
    ): GroupForum? {
        val origin = addressOf(url)?.toString() ?: return null
        return GroupForum(name, origin, logoUrl, origin in connected, new = origin !in seen)
    }
}

private fun httpsOnly(url: String): ForumAddress? =
    (ForumAddress.parse(url) as? ForumAddress.Parsed.Valid)?.address
