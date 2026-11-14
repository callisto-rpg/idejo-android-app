// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.data.Access
import org.idejo.app.core.data.InlineCard
import org.idejo.app.core.data.InlineCards
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.model.Mention
import javax.inject.Inject

/** An inline link's card (TV-22). */
data class InlineCardUi(
    val card: InlineCard? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val problem: ApiProblem? = null,
    val origin: String = "",
    /** **Follow** shows for signed-in members only, as on the web. */
    val signedIn: Boolean = false,
)

/** The card an inline link opens (TV-22), and **Follow** / **Unfollow** on a topic's. */
@HiltViewModel
class InlineCardViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        sessions: SessionManager,
    ) : ViewModel() {
        private val mention =
            Mention(
                type = checkNotNull(savedState["type"]),
                text = checkNotNull(savedState["text"]),
                id = savedState["id"],
                slug = savedState["slug"],
                name = savedState["name"],
            )
        private val session = sessions.active
        private val cards = session?.let(::InlineCards)
        private val mutableState =
            MutableStateFlow(
                InlineCardUi(
                    origin = session?.origin.orEmpty(),
                    signedIn =
                        session?.access?.value == Access.SignedIn,
                ),
            )
        val state: StateFlow<InlineCardUi> = mutableState.asStateFlow()

        init {
            load()
        }

        fun load() {
            mutableState.update { it.copy(loading = true, problem = null) }
            viewModelScope.launch {
                val result = cards?.load(mention)
                mutableState.update {
                    when (result) {
                        is ApiResult.Success -> it.copy(card = result.value, loading = false)
                        is ApiResult.Failure -> it.copy(loading = false, problem = result.problem)
                        null -> it.copy(loading = false)
                    }
                }
            }
        }

        fun follow(followed: Boolean) {
            val shown = mutableState.value.card as? InlineCard.Topic ?: return
            mutableState.update { it.copy(busy = true, problem = null) }
            viewModelScope.launch {
                val result = cards?.follow(shown.topic.id, followed)
                mutableState.update {
                    when (result) {
                        is ApiResult.Success -> {
                            val topic = shown.topic.copy(followed = result.value.followed)
                            it.copy(card = shown.copy(topic = topic), busy = false)
                        }

                        is ApiResult.Failure -> {
                            it.copy(busy = false, problem = result.problem)
                        }

                        null -> {
                            it.copy(busy = false)
                        }
                    }
                }
            }
        }
    }
