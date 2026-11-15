// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.ForumApi
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.model.Category
import org.idejo.app.core.model.TopicCreate
import org.idejo.app.core.model.TopicIcon
import javax.inject.Inject

/** **New topic**'s form (CO-19). */
data class NewTopicForm(
    val categories: List<Category> = emptyList(),
    val categoryId: String? = null,
    val title: String = "",
    val body: String = "",
    val tagIds: Set<String> = emptySet(),
    /** A moderator's choice, in a category whose topics are not topical already. */
    val topical: Boolean = false,
    val icons: List<TopicIcon> = emptyList(),
    val iconId: String? = null,
    /** The first message drawn as it will look, never live. */
    val preview: Boolean = false,
    val sending: Boolean = false,
    val error: ComposerError? = null,
    /** Waiting for a moderator's approval: its title. */
    val pending: String? = null,
    /** The topic started, to open. */
    val created: String? = null,
    /** The message the topic continues from. */
    val from: String? = null,
) {
    val category: Category? get() = categories.firstOrNull { it.id == categoryId }

    /** The category's own topics are topical, or a moderator made this one so. */
    val isTopical: Boolean get() = category?.topicType == "topical" || topical

    val mayMakeTopical: Boolean
        get() = category?.permissions?.moderate == true && category?.topicType != "topical"

    val needsTag: Boolean get() = category?.tags?.isNotEmpty() == true

    val ready: Boolean
        get() =
            category != null && title.isNotBlank() && body.isNotBlank() && !sending &&
                (!needsTag || tagIds.isNotEmpty()) &&
                (!isTopical || icons.isEmpty() || iconId != null)
}

/** What **New topic** asks of the forum. */
interface NewTopicBackend {
    suspend fun categories(): ApiResult<List<Category>>

    suspend fun icons(): ApiResult<List<TopicIcon>>

    suspend fun create(
        body: TopicCreate,
        from: String?,
    ): ApiResult<org.idejo.app.core.model.TopicCreated>
}

/** **New topic** (CO-19): its form, from where `!new-topic` started it. */
class NewTopic(
    private val backend: NewTopicBackend,
    private val scope: kotlinx.coroutines.CoroutineScope,
    start: NewTopicForm,
) {
    private val mutable = MutableStateFlow(start)
    val form: StateFlow<NewTopicForm> = mutable.asStateFlow()

    init {
        scope.launch {
            val all = (backend.categories() as? ApiResult.Success)?.value.orEmpty()
            val open = all.filter { it.permissions.createTopic && !it.archive }
            mutable.update { form ->
                form.copy(
                    categories = open,
                    categoryId = form.categoryId?.takeIf { id -> open.any { it.id == id } },
                )
            }
            iconsIfTopical()
        }
    }

    fun change(next: (NewTopicForm) -> NewTopicForm) {
        val before = mutable.value
        mutable.update { next(it).copy(error = null) }
        val after = mutable.value
        if (after.categoryId != before.categoryId) {
            mutable.update { it.copy(tagIds = emptySet(), topical = false) }
        }
        iconsIfTopical()
    }

    private fun iconsIfTopical() {
        val form = mutable.value
        if (!form.isTopical || form.icons.isNotEmpty()) return
        scope.launch {
            val icons = (backend.icons() as? ApiResult.Success)?.value.orEmpty()
            mutable.update { it.copy(icons = icons) }
        }
    }

    fun create() {
        val form = mutable.value
        if (!form.ready) return
        mutable.update { it.copy(sending = true) }
        scope.launch {
            val body =
                TopicCreate(
                    form.title.trim(),
                    form.body,
                    form.tagIds.toList(),
                    form.iconId.takeIf { form.isTopical },
                    form.topical,
                    form.categoryId,
                )
            when (val result = backend.create(body, form.from)) {
                is ApiResult.Success -> {
                    mutable.update {
                        it.copy(
                            sending = false,
                            created = result.value.topic?.id,
                            pending = result.value.pending?.let { p -> p.title ?: form.title },
                        )
                    }
                }

                is ApiResult.Failure -> {
                    mutable.update { it.copy(sending = false, error = errorOf(result.problem)) }
                }
            }
        }
    }
}

/** The calls of [NewTopic] through [api]. */
class ApiNewTopicBackend(
    private val calls: ForumCalls,
) : NewTopicBackend {
    override suspend fun categories() = calls.call { categories() }

    override suspend fun icons() = calls.call { topicIcons() }

    override suspend fun create(
        body: TopicCreate,
        from: String?,
    ) = calls.call { createTopic(body, from) }

    companion object {
        fun of(api: ForumApi) =
            ApiNewTopicBackend(
                object : ForumCalls {
                    override suspend fun <T> call(request: suspend ForumApi.() -> ApiResult<T>) =
                        api.request()
                },
            )
    }
}

/** The route's `categoryId`, `from`, `title` and `body` (Place.NewTopic). */
@HiltViewModel
class NewTopicViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        sessions: SessionManager,
    ) : ViewModel() {
        private val session = sessions.active

        /** The forum's address, for icons. */
        val origin: String = session?.origin.orEmpty()

        val newTopic: NewTopic? =
            session?.let { forum ->
                NewTopic(
                    ApiNewTopicBackend(
                        object : ForumCalls {
                            override suspend fun <T> call(
                                request: suspend ForumApi.() -> ApiResult<T>,
                            ) = forum.call(request)
                        },
                    ),
                    viewModelScope,
                    NewTopicForm(
                        categoryId = savedState["categoryId"],
                        title = savedState.get<String>("title").orEmpty(),
                        body = savedState.get<String>("body").orEmpty(),
                        from = savedState["from"],
                    ),
                )
            }
    }
