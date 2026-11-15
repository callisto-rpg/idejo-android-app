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
import org.idejo.app.core.model.Emoji
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.Suggestion
import org.idejo.app.core.model.Topic
import org.idejo.app.core.model.ComposerState as ServerComposer

/** How long "sent & committed" shows (CO-05). */
const val COMMITTED_SHOWN_MILLIS = 3_000L

/** The composer's calls for one topic (API guide §6). */
interface ComposerBackend {
    suspend fun composer(): ApiResult<ServerComposer>

    suspend fun typing(): ApiResult<ServerComposer>

    suspend fun commit(commit: Commit): ApiResult<CommitResult>

    suspend fun message(id: String): ApiResult<Message>

    suspend fun topic(id: String): ApiResult<Topic>

    suspend fun edit(
        id: String,
        bodyMd: String,
        finish: Boolean,
    ): ApiResult<Message>

    suspend fun delete(id: String): ApiResult<Message>

    /** What follows [sign] (CO-12). */
    suspend fun suggest(
        sign: Char,
        query: String,
    ): ApiResult<List<Suggestion>>

    /** The emoji whose names hold [query] (CO-10). */
    suspend fun emoji(query: String): ApiResult<List<Emoji>>
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

    override suspend fun topic(id: String) = calls.call { topic(id) }

    override suspend fun edit(
        id: String,
        bodyMd: String,
        finish: Boolean,
    ) = calls.call { editMessage(id, bodyMd, finish) }

    override suspend fun delete(id: String) = calls.call { deleteMessage(id) }

    override suspend fun suggest(
        sign: Char,
        query: String,
    ) = calls.call { suggest(sign, query, topicId) }

    override suspend fun emoji(query: String) = calls.call { emoji(query) }

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
@Suppress("TooManyFunctions") // One for each thing the member does, each a line.
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

    /** The suggestions after [sign] (CO-12); none on a failure. */
    suspend fun suggestions(
        sign: Char,
        query: String,
    ): List<Suggestion> = backend.suggest(sign, query).getOrNull().orEmpty()

    /** The emoji picker's emoji (CO-10). */
    suspend fun emoji(query: String): List<Emoji> = backend.emoji(query).getOrNull().orEmpty()

    /** **Reply** on [quote], above or below the open message (CO-16). */
    fun reply(
        quote: Quoted,
        markdown: String,
        above: Boolean,
    ) = dispatch(ComposerEvent.Reply(quote, markdown, above))

    fun cancelReply() = dispatch(ComposerEvent.CancelReply)

    /** Forwards message [messageId] from here (CO-17): it shows whole above the box. */
    fun forward(messageId: String) {
        scope.launch {
            val message = backend.message(messageId).getOrNull() ?: return@launch
            val title = backend.topic(message.topicId).getOrNull()?.title
            dispatch(ComposerEvent.Forward(Forwarding(message, title)))
        }
    }

    fun cancelForward() = dispatch(ComposerEvent.CancelForward)

    fun appendToOpen() = dispatch(ComposerEvent.AppendToOpen)

    /** **Edit** one's closed [message] (CO-18). */
    fun edit(message: Message) = dispatch(ComposerEvent.Edit(message))

    fun confirmDelete() = dispatch(ComposerEvent.ConfirmDelete)

    fun cancelDelete() = dispatch(ComposerEvent.CancelDelete)

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

                is ComposerEffect.SaveEdit -> {
                    val attempt = Commit(effect.bodyMd, effect.finish)
                    when (
                        val result =
                            backend.edit(
                                effect.messageId,
                                effect.bodyMd,
                                effect.finish,
                            )
                    ) {
                        is ApiResult.Success -> {
                            dispatch(ComposerEvent.Edited(result.value, effect.finish))
                        }

                        is ApiResult.Failure -> {
                            dispatch(ComposerEvent.Failed(attempt, result.problem))
                        }
                    }
                }

                is ComposerEffect.Delete -> {
                    when (val result = backend.delete(effect.messageId)) {
                        is ApiResult.Success -> {
                            dispatch(ComposerEvent.Deleted)
                        }

                        is ApiResult.Failure -> {
                            dispatch(ComposerEvent.Failed(Commit(""), result.problem))
                        }
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
