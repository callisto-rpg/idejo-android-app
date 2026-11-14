// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.getOrNull
import org.idejo.app.core.model.Warning
import org.idejo.app.core.realtime.IdejoEvent

/** The warnings waiting for the member (TV-11), oldest first: the card shows while any do. */
data class WarningsState(
    val warnings: List<Warning> = emptyList(),
    val sending: Boolean = false,
    val problem: ApiProblem? = null,
)

/**
 * The web's warning card for [session]'s member (TV-11, idejo MO-02): read from
 * `GET /me/warnings` when `GET /me` says one waits (`warning_pending`), and again when a
 * moderator warns them now (user:me's `warning` notification). **I understand** marks them
 * seen (`POST /me/warnings/acknowledge`).
 */
class Warnings(
    private val session: ForumSession,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(WarningsState())
    val state: StateFlow<WarningsState> = mutableState.asStateFlow()
    private var listening: Job? = null

    /** Listens for new warnings; reads the waiting ones when [pending]. */
    fun start(pending: Boolean) {
        listening?.cancel()
        listening =
            scope.launch {
                session.userEvents.collect { event ->
                    if (event is IdejoEvent.Notification && event.kind == WARNING) read()
                }
            }
        if (pending) scope.launch { read() }
    }

    /** **I understand**, or the card closed: the warnings are seen. */
    fun acknowledge() {
        if (mutableState.value.sending) return
        scope.launch {
            mutableState.update { it.copy(sending = true, problem = null) }
            when (val result = session.call { acknowledgeWarnings() }) {
                is ApiResult.Success -> {
                    mutableState.value = WarningsState()
                }

                is ApiResult.Failure -> {
                    mutableState.update { it.copy(sending = false, problem = result.problem) }
                }
            }
        }
    }

    /** The topic a warning's message is in now, to open it there; null when unreadable. */
    suspend fun topicOf(messageId: String): String? =
        session.call { message(messageId) }.getOrNull()?.topicId

    fun close() {
        listening?.cancel()
    }

    private suspend fun read() {
        session.call { warnings() }.getOrNull()?.let { warnings ->
            mutableState.update { it.copy(warnings = warnings) }
        }
    }

    private companion object {
        const val WARNING = "warning"
    }
}
