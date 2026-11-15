// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.ForumApi
import org.idejo.app.core.api.getOrNull
import org.idejo.app.core.data.ForumSession
import org.idejo.app.core.model.Commit
import org.idejo.app.core.model.CommitResult
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.ComposerState as ServerComposer

/** How long "sent & committed" shows (CO-05). */
const val COMMITTED_SHOWN_MILLIS = 3_000L

/** The composer's calls for one topic (API guide §6). */
interface ComposerBackend {
    suspend fun composer(): ApiResult<ServerComposer>

    suspend fun typing(): ApiResult<ServerComposer>

    suspend fun commit(commit: Commit): ApiResult<CommitResult>

    suspend fun message(id: String): ApiResult<Message>
}

/** Something that calls a forum's API: its session, or in tests a bare client. */
interface ForumCalls {
    suspend fun <T> call(request: suspend ForumApi.() -> ApiResult<T>): ApiResult<T>
}

/** The calls of topic [topicId]. */
class ApiComposerBackend(
    private val topicId: String,
    private val calls: ForumCalls,
) : ComposerBackend {
    override suspend fun composer() = calls.call { composer(topicId) }

    override suspend fun typing() = calls.call { typing(topicId) }

    override suspend fun commit(commit: Commit) = calls.call { commit(topicId, commit) }

    override suspend fun message(id: String) = calls.call { message(id) }

    companion object {
        /** Through the forum's session, which notices a refused token (SI-09). */
        fun of(
            session: ForumSession,
            topicId: String,
        ) = ApiComposerBackend(
            topicId,
            object : ForumCalls {
                override suspend fun <T> call(request: suspend ForumApi.() -> ApiResult<T>) =
                    session.call(request)
            },
        )

        fun of(
            api: ForumApi,
            topicId: String,
        ) = ApiComposerBackend(
            topicId,
            object : ForumCalls {
                override suspend fun <T> call(request: suspend ForumApi.() -> ApiResult<T>) =
                    api.request()
            },
        )
    }
}

/**
 * The composer of one topic: [ComposerMachine]'s state, and its effects run against
 * [backend] in [scope]. The screen sends what the member did and draws [state].
 */
class Composer(
    private val backend: ComposerBackend,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow(ComposerState())
    val state: StateFlow<ComposerState> = mutableState.asStateFlow()

    /** Reads the server's composer, and the open message when there is one (invariant 5). */
    fun refresh() {
        scope.launch {
            val composer = backend.composer().getOrNull() ?: return@launch
            val open = composer.openMessageId?.let { backend.message(it).getOrNull() }
            dispatch(ComposerEvent.Server(composer, open, clock()))
        }
    }

    fun typed(text: String) = dispatch(ComposerEvent.Typed(text))

    /**
     * The box's text changed to [text], from [before]: a newline the soft keyboard typed is
     * an Enter, never inserted (CO-03).
     */
    fun edited(
        before: String,
        text: String,
    ) {
        val withoutReturn = softReturn(before, text)
        if (withoutReturn != null) enter() else typed(text)
    }

    fun enter() = dispatch(ComposerEvent.Enter(clock()))

    fun complete() = dispatch(ComposerEvent.Complete)

    fun errorSeen() = dispatch(ComposerEvent.ErrorSeen)

    private fun dispatch(event: ComposerEvent) {
        var effects = emptyList<ComposerEffect>()
        mutableState.update { state ->
            ComposerMachine.reduce(state, event).also { effects = it.effects }.state
        }
        effects.forEach(::run)
        if (event is ComposerEvent.Answered && event.attempt.breakMessage) {
            val committed = event.result.message?.id ?: return
            scope.launch {
                delay(COMMITTED_SHOWN_MILLIS)
                if (state.value.committedId == committed) dispatch(ComposerEvent.CommittedShown)
            }
        }
    }

    private fun run(effect: ComposerEffect) {
        scope.launch {
            when (effect) {
                ComposerEffect.Typing -> {
                    backend.typing().getOrNull()?.let {
                        dispatch(ComposerEvent.Server(it, atMillis = clock()))
                    }
                }

                is ComposerEffect.Send -> {
                    when (val result = backend.commit(effect.commit)) {
                        is ApiResult.Success -> {
                            dispatch(ComposerEvent.Answered(effect.commit, result.value, clock()))
                        }

                        is ApiResult.Failure -> {
                            dispatch(ComposerEvent.Failed(effect.commit, result.problem))
                        }
                    }
                }
            }
        }
    }
}
