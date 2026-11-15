// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.idejo.app.core.api.getOrNull
import org.idejo.app.core.data.Access
import org.idejo.app.core.data.AppChoices
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.data.TutorialBubbles
import org.idejo.app.core.model.Me
import javax.inject.Inject

/**
 * The composer of the topic page (CO): the route's `id` is the topic. It lives as long as the
 * page's back-stack entry, as the topic channel does (CO-21): going to the background closes
 * the open message after 30 s on the server, and the app does not fight it (invariant 7).
 */
@HiltViewModel
class ComposerViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        sessions: SessionManager,
        choices: AppChoices,
    ) : ViewModel() {
        private val session = sessions.active?.takeIf { it.access.value == Access.SignedIn }
        private val topicId: String = checkNotNull(savedState["id"])

        /** Null for visitors: no composer. */
        val composer: Composer? =
            session?.let { Composer(ApiComposerBackend.of(it, topicId), viewModelScope) }

        private val mutableMe = MutableStateFlow<Me?>(null)

        /** The author, drawn on the composer's head (CO-05). */
        val me: StateFlow<Me?> = mutableMe.asStateFlow()

        /** **Tutorial bubbles**, one choice for the whole app (CO-06, SH-21). */
        val bubbles: StateFlow<TutorialBubbles> =
            choices.tutorialBubbles.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                TutorialBubbles.All,
            )

        /** The forum's address, for pictures. */
        val origin: String = session?.origin.orEmpty()

        init {
            composer?.refresh()
            // A message forwarded here from the forward card (CO-17), once.
            val forward: String? = savedState["forward"]
            if (forward != null && savedState.get<Boolean>(FORWARDED) != true) {
                savedState[FORWARDED] = true
                composer?.forward(forward)
            }
            session?.let { forum ->
                viewModelScope.launch { mutableMe.value = forum.call { me() }.getOrNull() }
            }
        }
    }

private const val FORWARDED = "forwarded"
