// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.getOrNull
import org.idejo.app.core.model.Me
import org.idejo.app.core.model.Server

/** The cards a member meets over the forum (BR-07, BR-11). */
enum class MemberCard {
    /** **Welcome to …**: About, Rules and the self-select roles, until **Continue**. */
    Welcome,

    /** **Getting started**, after a sign-in once welcomed, until closed. */
    GettingStarted,
}

/** What Rules & Roles and the cards show: the server's About, Rules and roles, and the member. */
data class WelcomeState(
    val server: Server? = null,
    val me: Me? = null,
    /** The roles ticked, by id; the ones held until the member changes them. */
    val chosen: Set<String> = emptySet(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    /** Set once roles are saved, until they change again. */
    val saved: Boolean = false,
    val problem: ApiProblem? = null,
) {
    /** The card due now, the welcome card first, as the web. */
    val card: MemberCard?
        get() =
            when {
                me == null -> null
                !me.welcomed -> MemberCard.Welcome
                me.introDue -> MemberCard.GettingStarted
                else -> null
            }

    /** The self-select roles the member holds, by id. */
    val held: Set<String>
        get() {
            val selectable =
                server
                    ?.roles
                    .orEmpty()
                    .mapNotNull { it.id }
                    .toSet()
            return me
                ?.roles
                .orEmpty()
                .mapNotNull { it.id }
                .filter { it in selectable }
                .toSet()
        }
}

/**
 * Rules & Roles and the welcome and getting-started cards for [session]'s forum (BR-07,
 * BR-11): `GET /server` and `GET /me`, roles taken and left one by one (`PUT`/`DELETE
 * /me/roles/{id}`), the welcome card marked seen (`POST /me/welcome`) and Getting started
 * closed (`POST /me/intro`).
 */
class Welcome(
    private val session: ForumSession,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(WelcomeState())
    val state: StateFlow<WelcomeState> = mutableState.asStateFlow()

    fun refresh() {
        scope.launch {
            val (server, me) =
                coroutineScope {
                    val server = async { session.refreshServer() }
                    val me = async { session.call { me() } }
                    server.await() to me.await()
                }
            val failure = (server as? ApiResult.Failure) ?: (me as? ApiResult.Failure)
            mutableState.update { state ->
                val read =
                    state.copy(
                        server = server.getOrNull() ?: state.server,
                        me = me.getOrNull() ?: state.me,
                        loading = false,
                        problem = failure?.problem,
                    )
                read.copy(chosen = read.held)
            }
        }
    }

    /** A role ticked or unticked; nothing is sent until [save] or [continueWelcome]. */
    fun tick(roleId: String) =
        mutableState.update {
            val chosen = if (roleId in it.chosen) it.chosen - roleId else it.chosen + roleId
            it.copy(chosen = chosen, saved = false)
        }

    /** **Save my roles** (Rules & Roles): the roles, then the welcome card seen, as the web. */
    fun save() = scope.launch { saveRoles() }

    /** **Continue** on the welcome card. */
    fun continueWelcome() = scope.launch { saveRoles() }

    /** **Close**, or with [hide] **Close and hide this in the future**. */
    fun closeIntro(hide: Boolean) {
        scope.launch {
            mutableState.update { it.copy(saving = true, problem = null) }
            done(session.call { intro(hide) })
        }
    }

    private suspend fun saveRoles() {
        val state = state.value
        mutableState.update { it.copy(saving = true, problem = null) }
        val changes =
            (state.chosen - state.held).map { it to true } +
                (state.held - state.chosen).map { it to false }
        for ((id, taken) in changes) {
            val result = session.call { role(id, taken) }
            if (result is ApiResult.Failure) return done(result)
        }
        done(session.call { welcome() }, saved = true)
    }

    private fun done(
        result: ApiResult<Me>,
        saved: Boolean = false,
    ) = mutableState.update { state ->
        when (result) {
            is ApiResult.Success -> {
                val next = state.copy(me = result.value, saving = false, saved = saved)
                next.copy(chosen = next.held)
            }

            is ApiResult.Failure -> {
                state.copy(saving = false, problem = result.problem)
            }
        }
    }
}
