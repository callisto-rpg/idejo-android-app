// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.component

import org.idejo.app.core.designsystem.theme.DotKind
import org.idejo.app.core.model.MentionState
import org.idejo.app.core.model.Topic

/** A topic's dot (SH-32): the strongest reason it has news, or none when it has none. */
fun Topic.dot(): DotKind? = mentionState.dot() ?: if (unreadCount > 0) DotKind.NewMessages else null

/** The dot for [MentionState] (SH-32), as a forum's in the logo menu (SH-12); none for none. */
fun MentionState.dot(): DotKind? =
    when (this) {
        MentionState.Mention -> DotKind.Mention
        MentionState.Reply -> DotKind.Reply
        MentionState.Role -> DotKind.RoleMention
        MentionState.New -> DotKind.NewMessages
        MentionState.None -> null
    }

/** The **Unread** button's colour from the counter's `level` (SH-01). */
fun unreadLevel(level: String?): DotKind? =
    when (level) {
        "mention" -> DotKind.Mention
        "reply" -> DotKind.Reply
        "role" -> DotKind.RoleMention
        "new" -> DotKind.NewMessages
        else -> null
    }
