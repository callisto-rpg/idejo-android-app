// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import org.idejo.app.core.model.Me
import org.idejo.app.core.model.Mention
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.MessageState

/** How an unread message names the member (TV-07): in their dot's colour, red or blue. */
enum class Glow {
    /** `@member`: red. */
    Mention,

    /** `+role`, a role the member holds: blue. */
    Role,
}

/**
 * The unread messages that glow as they are reached (TV-07), as the web's: from the first
 * unread one ([from]) on, by others and not deleted, those that @mention [me] (red), else
 * +mention one of their roles (blue, matched by slug, IX-12). None without [from] or [me].
 */
fun mentionGlows(
    messages: List<Message>,
    from: String?,
    me: Me?,
): Map<String, Glow> {
    if (from == null || me == null) return emptyMap()
    val roles = roleSlugs(me)
    return messages
        .dropWhile { it.id != from }
        .filter { it.author.id != me.id && it.state != MessageState.Deleted }
        .mapNotNull { message ->
            when {
                message.mentions.any { it.isUser(me) } -> message.id to Glow.Mention
                message.mentions.any { it.isRoleOf(roles) } -> message.id to Glow.Role
                else -> null
            }
        }.toMap()
}

/** Whether this inline link is the member (@) or one of their roles (+): the tag that pulses. */
fun Mention.namesMember(me: Me): Boolean = isUser(me) || isRoleOf(roleSlugs(me))

private fun roleSlugs(me: Me): Set<String> = me.roles.mapNotNull { it.slug?.lowercase() }.toSet()

private fun Mention.isUser(me: Me): Boolean = type == "user" && id == me.id

private fun Mention.isRoleOf(roles: Set<String>): Boolean =
    type == "role" && (slug ?: id)?.lowercase() in roles
