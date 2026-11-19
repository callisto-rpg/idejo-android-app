// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

/** How a sign-in [origin] started ended: signed in, or not (cancelled, refused, failed). */
data class SignInOutcome(
    val origin: String,
    val signedIn: Boolean,
)

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

    private val mutableOutcomes = MutableSharedFlow<SignInOutcome>(extraBufferCapacity = OUTCOMES)

    /** Each sign-in's end, for flows that run several in a row (SI-06). */
    val outcomes: SharedFlow<SignInOutcome> = mutableOutcomes.asSharedFlow()

    /** What the token is for. */
    private enum class Purpose {
        /** A forum added by address (SI-01): opened. */
        Add,

        /** Signing in again to a forum the app holds (SI-05, SI-09). */
        Again,

        /** The group's inbox (SI-06): idejo ID mode. */
        Inbox,

        /** A forum of the group, connected from the inbox (SI-06): held, not opened. */
        Connect,
    }

    private class Pending(
        val found: DiscoveryResult.Found,
        val request: SignInRequest,
        val purpose: Purpose,
    ) {
        val origin: String get() = found.address.toString()
    }

    /** The forum's sign-in page for [found], or null when it has none (state: Failed). */
    fun start(
        found: DiscoveryResult.Found,
        deviceName: String,
    ): HttpUrl? = begin(found, deviceName, Purpose.Add)

    /** SI-06: the group's inbox, for idejo ID mode. */
    fun startInbox(
        found: DiscoveryResult.Found,
        deviceName: String,
    ): HttpUrl? = begin(found, deviceName, Purpose.Inbox)

    /** SI-06: one of the group's forums, connected while the inbox stays on screen. */
    fun startConnect(
        found: DiscoveryResult.Found,
        deviceName: String,
    ): HttpUrl? = begin(found, deviceName, Purpose.Connect)

    /** Signing in again to the open forum: a visitor, or after "You were signed out". */
    fun startAgain(
        session: ForumSession,
        deviceName: String,
    ): HttpUrl? =
        begin(
            DiscoveryResult.Found(session.address, session.record.value.discovery),
            deviceName,
            Purpose.Again,
        )

    private fun begin(
        found: DiscoveryResult.Found,
        deviceName: String,
        purpose: Purpose,
    ): HttpUrl? {
        val name = found.discovery.name
        val request = found.discovery.oauth?.let { SignInRequest(found.address, it) }
        val url = request?.authorizeUrl(deviceName)
        if (request == null || url == null) {
            pending = null
            mutableState.value = SignInState.Failed(name, SignInState.Reason.NoSignIn)
            mutableOutcomes.tryEmit(SignInOutcome(found.address.toString(), signedIn = false))
            return null
        }
        pending = Pending(found, request, purpose)
        mutableState.value = SignInState.Waiting(name)
        return url
    }

    /** The Custom Tab was closed without an answer: back where the member was. */
    fun cancelIfWaiting() {
        if (mutableState.value is SignInState.Waiting) {
            pending?.let { mutableOutcomes.tryEmit(SignInOutcome(it.origin, signedIn = false)) }
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
                mutableOutcomes.tryEmit(SignInOutcome(current.origin, signedIn = false))
            }

            is SignInRequest.Callback.Error -> {
                fail(current, SignInState.Reason.Refused)
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
        when (val result = oauth.exchange(current.request, code)) {
            is ApiResult.Success -> {
                val grant = result.value
                when (current.purpose) {
                    Purpose.Add -> sessions.add(current.found, grant)
                    Purpose.Again -> sessions.signedIn(current.origin, grant)
                    Purpose.Inbox -> sessions.signedInToInbox(current.found, grant)
                    Purpose.Connect -> sessions.connect(current.found, grant)
                }
                mutableState.value = SignInState.Idle
                mutableOutcomes.tryEmit(SignInOutcome(current.origin, signedIn = true))
            }

            is ApiResult.Failure -> {
                fail(
                    current,
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
        current: Pending,
        reason: SignInState.Reason,
    ) {
        pending = null
        mutableState.value = SignInState.Failed(current.found.discovery.name, reason)
        mutableOutcomes.tryEmit(SignInOutcome(current.origin, signedIn = false))
    }

    private companion object {
        const val OUTCOMES = 8
    }
}
