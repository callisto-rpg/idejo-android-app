// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import java.util.concurrent.atomic.AtomicReference

/** Where in the inbox the logo menu sends the member (SH-12 to SH-14). */
sealed interface InboxShortcut {
    /** **All my topics**: the inbox's **Topics**. */
    data object Topics : InboxShortcut

    /** **Private Conversations**: the inbox's **Chats**. */
    data object Chats : InboxShortcut

    /** **Find more forums** (SI-08): the group's directory. */
    data object FindForums : InboxShortcut

    /** **Connect** beside a forum of the group: its sign-in, from the inbox's **Forums**. */
    data class Connect(
        val url: String,
    ) : InboxShortcut
}

/**
 * The shortcut waiting for the inbox to show, as [PendingLinks] for a forum: offered before
 * `SessionManager.openInbox`, taken once by the inbox's screens.
 */
class InboxShortcuts {
    private val pending = AtomicReference<InboxShortcut?>(null)

    fun offer(shortcut: InboxShortcut) = pending.set(shortcut)

    /** The shortcut waiting, if [match] takes it; it is then gone. */
    fun take(match: (InboxShortcut) -> Boolean = { true }): InboxShortcut? =
        pending.getAndUpdate { if (it != null && match(it)) null else it }?.takeIf(match)
}
