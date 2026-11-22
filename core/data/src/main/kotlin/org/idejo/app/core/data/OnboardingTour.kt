// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.model.Onboarding

/**
 * Where a new member is on the onboarding tour (IX-18; steward request, 2026-11-22), shared
 * by the shell, which draws it, and the composer, which moves it on.
 *
 * The server says whether the member is on it (`GET /me`'s `onboarding`); the composer of
 * the onboarding topic tells [composer] whether the member's message there is open, [closed]
 * when it was completed (three Enters), and [enteredOnce] when an Enter was not followed by
 * the two others. Closing it finishes the tour on the server (`POST /me/onboarded`) at once;
 * the last card stays until [dismiss].
 */
class OnboardingTour internal constructor(
    private val me: MeRepository,
    private val scope: CoroutineScope,
) {
    /** What the shell needs to draw the tour. */
    data class State(
        /** The topic and question; null: no tour. */
        val onboarding: Onboarding? = null,
        /** The member's message in the onboarding topic is open (sent, not completed). */
        val open: Boolean = false,
        /** The message was completed: the last card, until **Close**. */
        val finished: Boolean = false,
        /** Counts the lone Enters, each one a "three times" hint. */
        val loneEnters: Int = 0,
    ) {
        val active: Boolean get() = onboarding != null
    }

    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    init {
        scope.launch {
            me.state.collect { member ->
                mutableState.update {
                    // The last card outlives the server's answer to `POST /me/onboarded`.
                    if (it.finished) it else it.copy(onboarding = member?.onboarding)
                }
            }
        }
    }

    private fun touring(topicId: String): Boolean {
        val state = mutableState.value
        return !state.finished && state.onboarding?.topicId == topicId
    }

    /** The onboarding topic's composer: whether the member's message there is [open]. */
    fun composer(
        topicId: String,
        open: Boolean,
    ) {
        if (touring(topicId)) mutableState.update { it.copy(open = open) }
    }

    /** The member's message in the onboarding topic was completed: the tour is done. */
    fun closed(topicId: String) {
        if (!touring(topicId)) return
        mutableState.update { it.copy(open = false, finished = true) }
        scope.launch { me.onboarded() }
    }

    /** One Enter in the onboarding topic, not followed by the two others in time. */
    fun enteredOnce(topicId: String) {
        if (touring(topicId) && mutableState.value.open) {
            mutableState.update { it.copy(loneEnters = it.loneEnters + 1) }
        }
    }

    /** **Close** on the last card, or the member left it: the tour is over. */
    fun dismiss() {
        val wasOn = me.state.value?.onboarding != null
        mutableState.value = State()
        // The server did not hear it yet (offline): once more.
        if (wasOn) scope.launch { me.onboarded() }
    }
}
