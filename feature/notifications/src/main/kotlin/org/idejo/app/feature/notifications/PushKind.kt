// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.notifications

import androidx.annotation.StringRes

/**
 * The kinds of push (NT-05), each its own channel per forum, so Android's settings can
 * silence any of them: from idejo's `tag` (`reply-{topic}`, `mentions`, `role-mentions`,
 * `followed`, `private`, `warning`; anything newer, and the test, is "Other").
 */
enum class PushKind(
    val id: String,
    @StringRes val label: Int,
) {
    Replies("replies", R.string.channel_replies),
    Mentions("mentions", R.string.channel_mentions),
    RoleMentions("role_mentions", R.string.channel_role_mentions),
    Followed("followed", R.string.channel_followed),
    Private("private", R.string.channel_private),
    Warnings("warnings", R.string.channel_warnings),
    Other("other", R.string.channel_other),
    ;

    companion object {
        fun of(tag: String?): PushKind =
            when {
                tag == null -> Other
                tag.startsWith("reply") -> Replies
                tag == "mentions" -> Mentions
                tag == "role-mentions" -> RoleMentions
                tag == "followed" -> Followed
                tag == "private" -> Private
                tag == "warning" -> Warnings
                else -> Other
            }
    }
}
