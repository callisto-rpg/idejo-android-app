// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import okhttp3.HttpUrl
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.DiscoveryClient
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.model.Community

/** One of the group's forums the member joined, and whether the app holds a token for it. */
data class GroupForum(
    val name: String,
    /** Its address as the inbox gives it: `https://garden.example.org/`. */
    val url: String,
    val logoUrl: String? = null,
    val connected: Boolean = false,
)

/**
 * The group's forums in idejo ID mode (SI-06): those the member joined, from the inbox's
 * `GET /me/communities` (API guide §9), and connecting them: each one's own sign-in page in
 * a Custom Tab, one tap with the idejo ID session, its token kept beside the inbox's.
 */
class GroupForums(
    private val sessions: SessionManager,
    private val signIn: SignInCoordinator,
    private val discovery: DiscoveryClient,
    private val deviceName: String,
    /** A forum's address from the inbox's list; null for one the app may not reach (https). */
    private val addressOf: (String) -> ForumAddress? = ::httpsOnly,
) {
    /** The forums joined, read from the inbox, each marked connected or not. */
    suspend fun joined(): ApiResult<List<GroupForum>> {
        val inbox =
            sessions.inbox
                ?: return ApiResult.Failure(
                    ApiProblem.Network(java.io.IOException("No inbox open")),
                )
        val connected =
            sessions.state
                .first()
                .forums
                .filter { it.sealedToken != null }
                .map { it.origin }
                .toSet()
        return when (val result = inbox.call { communities() }) {
            is ApiResult.Success -> {
                ApiResult.Success(
                    result.value.mapNotNull { community -> community.forum(connected) },
                )
            }

            is ApiResult.Failure -> {
                result
            }
        }
    }

    /**
     * Connects [forum] (SI-06): its sign-in page is handed to [openTab]; true once the token
     * is kept, false when the member closed the page, refused, or the forum could not be
     * reached. The next forum waits for this one's end.
     */
    suspend fun connect(
        forum: GroupForum,
        openTab: suspend (HttpUrl) -> Boolean,
    ): Boolean {
        val address = addressOf(forum.url) ?: return false
        val found = discovery.discover(address) as? DiscoveryResult.Found ?: return false
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
                return@coroutineScope false
            }
            val signedIn = outcome.await().signedIn
            // A failure shows as "Not connected" in the list, not as a dialog.
            signIn.dismiss()
            signedIn
        }
    }

    private fun Community.forum(connected: Set<String>): GroupForum? {
        val origin = addressOf(url)?.toString() ?: return null
        return GroupForum(name, origin, logoUrl, origin in connected)
    }
}

private fun httpsOnly(url: String): ForumAddress? =
    (ForumAddress.parse(url) as? ForumAddress.Parsed.Valid)?.address
