// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where in the inbox the logo menu sends the member (SH-12 to SH-14). */
sealed interface InboxShortcut {
    /** **All my forums**: the inbox's **Forums** (steward request 2026-11-23). */
    data object Forums : InboxShortcut

    /** **All my topics**: the inbox's **Topics**. */
    data object Topics : InboxShortcut

    /** **Private Conversations**: the inbox's **Chats**. */
    data object Chats : InboxShortcut

    /** **Find more forums** (SI-08): the group's directory. */
    data object FindForums : InboxShortcut

    /** **Settings for all forums** (ST-07): the inbox's Profile and account. */
    data object Settings : InboxShortcut

    /** **Connect** beside a forum of the group: its sign-in, from the inbox's **Forums**. */
    data class Connect(
        val url: String,
    ) : InboxShortcut
}

/**
 * The shortcut waiting for the inbox to show, as [PendingLinks] for a forum: offered before
 * `SessionManager.openInbox`, or by the inbox's own forum menu while it is shown ([waiting]
 * tells its screens), taken once by the inbox's screens.
 */
class InboxShortcuts {
    private val pending = MutableStateFlow<InboxShortcut?>(null)

    /** The shortcut waiting, for the inbox's screens already shown. */
    val waiting: StateFlow<InboxShortcut?> = pending.asStateFlow()

    fun offer(shortcut: InboxShortcut) {
        pending.value = shortcut
    }

    /** The shortcut waiting, if [match] takes it; it is then gone. */
    fun take(match: (InboxShortcut) -> Boolean = { true }): InboxShortcut? {
        while (true) {
            val now = pending.value ?: return null
            if (!match(now)) return null
            if (pending.compareAndSet(now, null)) return now
        }
    }
}
