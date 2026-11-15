// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The member's choices for the whole app, not for one forum. */
@Singleton
class AppChoices
    @Inject
    constructor(
        private val store: ForumStore,
    ) {
        /** **Tutorial bubbles** (SH-21): one for every forum (steward decision, 2026-11-15). */
        val tutorialBubbles: Flow<TutorialBubbles>
            get() = store.state.map { it.tutorialBubbles }.distinctUntilChanged()

        suspend fun tutorialBubbles(choice: TutorialBubbles) {
            store.update { it.copy(tutorialBubbles = choice) }
        }
    }
