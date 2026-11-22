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

        /**
         * The notifications bar was dismissed in the last 14 days (NT-07), as the web's
         * banner (idejo PN-21); kept on this device, for every forum.
         */
        fun pushBarQuiet(now: () -> Long = System::currentTimeMillis): Flow<Boolean> =
            store.state
                .map { state -> state.pushBarDismissedAt?.let { now() - it < QUIET_MS } == true }
                .distinctUntilChanged()

        suspend fun dismissPushBar(now: Long = System.currentTimeMillis()) {
            store.update { it.copy(pushBarDismissedAt = now) }
        }

        /** Push turned off on this device (App settings, NT-06), for every forum. */
        val pushOff: Flow<Boolean>
            get() = store.state.map { it.pushOff }.distinctUntilChanged()

        suspend fun pushOff(off: Boolean) = store.update { it.copy(pushOff = off) }

        /** Text size, haptics and reduced motion (ST-22). */
        val display: Flow<AppDisplay>
            get() =
                store.state
                    .map { AppDisplay(it.messageText, it.haptics, it.reduceMotion) }
                    .distinctUntilChanged()

        suspend fun messageText(choice: MessageText) =
            store.update { it.copy(messageText = choice) }

        suspend fun haptics(on: Boolean) = store.update { it.copy(haptics = on) }

        suspend fun reduceMotion(on: Boolean) = store.update { it.copy(reduceMotion = on) }

        /** The image cache's limit (ST-21); the cache takes it at the app's next start. */
        val cacheLimitMb: Flow<Int>
            get() = store.state.map { it.cacheLimitMb }.distinctUntilChanged()

        suspend fun cacheLimitMb(megabytes: Int) =
            store.update { it.copy(cacheLimitMb = megabytes) }

        /** The inbox's Topics toolbar (IB-03), the same at every launch. */
        val inboxTopics: Flow<InboxTopicChoices>
            get() = store.state.map { it.inboxTopics }.distinctUntilChanged()

        suspend fun inboxTopics(transform: (InboxTopicChoices) -> InboxTopicChoices) =
            store.update { it.copy(inboxTopics = transform(it.inboxTopics)) }

        private companion object {
            const val QUIET_MS = 14L * 24 * 60 * 60 * 1000
        }
    }
