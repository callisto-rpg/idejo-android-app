// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.OAuthClient
import org.idejo.app.core.api.SignInRequest

/** Where a sign-in is (SI-03, SI-04). */
sealed interface SignInState {
    data object Idle : SignInState

    /** The forum's page is open in a Custom Tab. */
    data class Waiting(
        val forumName: String,
    ) : SignInState

    /** The code came back and is being exchanged. */
    data class Exchanging(
        val forumName: String,
    ) : SignInState

    data class Failed(
        val forumName: String,
        val reason: Reason,
    ) : SignInState

    enum class Reason {
        /** Discovery's `authorize` leads off the forum (SY-04), or it has no `oauth`. */
        NoSignIn,

        /** The forum's page answered with an error, or the exchange was refused. */
        Refused,

        /** No answer from the forum. */
        Unreachable,

        /** The app was closed while the page was open: the sign-in it started is gone. */
        Expired,
    }
}

/**
 * The one sign-in in progress. [start] gives the page to open; the redirect comes back to
 * [complete] through the callback activity. Cancel on the forum's page returns quietly
 * (SI-03); a redirect for another `state` is ignored. The verifier only lives in memory: a
 * sign-in survives no process death, and starts again.
 */
class SignInCoordinator(
    private val sessions: SessionManager,
    http: OkHttpClient,
) {
    private val oauth = OAuthClient(http)
    private var pending: Pending? = null

    private val mutableState = MutableStateFlow<SignInState>(SignInState.Idle)
    val state: StateFlow<SignInState> = mutableState.asStateFlow()

    private class Pending(
        val found: DiscoveryResult.Found,
        val request: SignInRequest,
        /** Signing in again to a forum the app holds (SI-05, SI-09). */
        val again: Boolean,
    )

    /** The forum's sign-in page for [found], or null when it has none (state: Failed). */
    fun start(
        found: DiscoveryResult.Found,
        deviceName: String,
    ): HttpUrl? = begin(found, deviceName, again = false)

    /** Signing in again to the open forum: a visitor, or after "You were signed out". */
    fun startAgain(
        session: ForumSession,
        deviceName: String,
    ): HttpUrl? =
        begin(
            DiscoveryResult.Found(session.address, session.record.value.discovery),
            deviceName,
            true,
        )

    private fun begin(
        found: DiscoveryResult.Found,
        deviceName: String,
        again: Boolean,
    ): HttpUrl? {
        val name = found.discovery.name
        val request = found.discovery.oauth?.let { SignInRequest(found.address, it) }
        val url = request?.authorizeUrl(deviceName)
        if (request == null || url == null) {
            pending = null
            mutableState.value = SignInState.Failed(name, SignInState.Reason.NoSignIn)
            return null
        }
        pending = Pending(found, request, again)
        mutableState.value = SignInState.Waiting(name)
        return url
    }

    /** The Custom Tab was closed without an answer: back where the member was. */
    fun cancelIfWaiting() {
        if (mutableState.value is SignInState.Waiting) {
            pending = null
            mutableState.value = SignInState.Idle
        }
    }

    /** The failure was shown. */
    fun dismiss() {
        if (mutableState.value is SignInState.Failed) mutableState.value = SignInState.Idle
    }

    /** The redirect to `org.idejo.app:/oauth/callback`, from the callback activity. */
    suspend fun complete(redirect: String) {
        val current = pending
        if (current == null) {
            if (mutableState.value !is SignInState.Exchanging) {
                mutableState.value = SignInState.Failed("", SignInState.Reason.Expired)
            }
            return
        }
        val name = current.found.discovery.name
        when (val callback = current.request.callback(redirect)) {
            SignInRequest.Callback.WrongState, SignInRequest.Callback.Invalid -> {
                return
            }

            SignInRequest.Callback.Denied -> {
                pending = null
                mutableState.value = SignInState.Idle
            }

            is SignInRequest.Callback.Error -> {
                fail(name, SignInState.Reason.Refused)
            }

            is SignInRequest.Callback.Code -> {
                pending = null
                mutableState.value = SignInState.Exchanging(name)
                exchange(current, callback.code)
            }
        }
    }

    private suspend fun exchange(
        current: Pending,
        code: String,
    ) {
        val name = current.found.discovery.name
        when (val result = oauth.exchange(current.request, code)) {
            is ApiResult.Success -> {
                if (current.again) {
                    sessions.signedIn(current.found.address.toString(), result.value)
                } else {
                    sessions.add(current.found, result.value)
                }
                mutableState.value = SignInState.Idle
            }

            is ApiResult.Failure -> {
                fail(
                    name,
                    if (result.problem is org.idejo.app.core.api.ApiProblem.Server) {
                        SignInState.Reason.Refused
                    } else {
                        SignInState.Reason.Unreachable
                    },
                )
            }
        }
    }

    private fun fail(
        name: String,
        reason: SignInState.Reason,
    ) {
        pending = null
        mutableState.value = SignInState.Failed(name, reason)
    }
}
