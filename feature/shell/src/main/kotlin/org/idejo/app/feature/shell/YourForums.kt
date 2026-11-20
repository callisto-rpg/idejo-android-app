// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.idejo.app.core.data.ForumRecord
import org.idejo.app.core.data.GroupForum
import org.idejo.app.core.data.InboxTopicsState
import org.idejo.app.core.data.logoOf
import org.idejo.app.core.designsystem.component.Dot
import org.idejo.app.core.designsystem.component.ForumLogo
import org.idejo.app.core.designsystem.component.dot
import org.idejo.app.core.model.MentionState

/** A forum in the logo menu's **Your forums** (SH-12). */
@Immutable
data class YourForum(
    val origin: String,
    val name: String,
    val logoUrl: String? = null,
    /** Its strongest reason for news, as the sidebar's dots; None without news. */
    val level: MentionState = MentionState.None,
    /** Held by the app: tapping it switches to it, and it may be removed (SH-15). */
    val held: Boolean = true,
    /** One of the group's forums without a token here: **Connect** (SI-06). */
    val connect: Boolean = false,
    /** Joined since the member last saw the group's forums (SI-07). */
    val new: Boolean = false,
    /** The forum open. */
    val current: Boolean = false,
)

/**
 * **Your forums** (SH-12): every forum the app holds, in the order added, then the group's
 * forums the member joined that the app does not hold, to connect. [read] gives the dots.
 */
internal fun yourForums(
    held: List<ForumRecord>,
    joined: List<GroupForum>,
    read: InboxTopicsState,
    current: String,
): List<YourForum> {
    val group = joined.associateBy { it.url }
    val kept =
        held.map { record ->
            val member = group[record.origin]
            YourForum(
                origin = record.origin,
                name = record.name,
                logoUrl = logoOf(record),
                level = read.levels[record.origin] ?: MentionState.None,
                connect = record.sealedToken == null && member != null,
                new = member?.new == true && record.sealedToken == null,
                current = record.origin == current,
            )
        }
    val origins = held.map { it.origin }.toSet()
    val waiting =
        joined.filter { it.url !in origins }.map {
            YourForum(it.url, it.name, it.logoUrl, held = false, connect = true, new = it.new)
        }
    return kept + waiting
}

/** What the member does with a forum of **Your forums** (SH-12, SH-15). */
interface YourForumsActions {
    fun enter(forum: YourForum)

    fun connect(forum: YourForum)

    fun remove(forum: YourForum)

    fun openInBrowser(forum: YourForum)
}

/** The list (SH-12), each forum long-pressed for **Remove from app** and **Open in browser**. */
@Composable
internal fun YourForumsList(
    forums: List<YourForum>,
    actions: YourForumsActions,
) {
    var removing by remember { mutableStateOf<YourForum?>(null) }
    forums.forEach { forum -> ForumItem(forum, actions) { removing = forum } }
    removing?.let { forum ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.accounts_remove_title, forum.name)) },
            text = { Text(stringResource(R.string.accounts_remove_text)) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    actions.remove(forum)
                }) { Text(stringResource(R.string.accounts_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ForumItem(
    forum: YourForum,
    actions: YourForumsActions,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics { selected = forum.current }
                .combinedClickable(
                    role = Role.Button,
                    onLongClickLabel = stringResource(R.string.your_forums_more),
                    onLongClick = { menu = true },
                    onClick = {
                        if (forum.held) actions.enter(forum) else actions.connect(forum)
                    },
                ).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Name(forum)
            forum.level.dot()?.let { Dot(it) }
            if (forum.new) {
                // Joined since last seen (SI-07), as on the logo.
                val joined = stringResource(R.string.logo_menu_new_forum)
                Box(
                    Modifier
                        .semantics { contentDescription = joined }
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                )
            }
            if (forum.connect) {
                TextButton(onClick = { actions.connect(forum) }) {
                    Text(stringResource(R.string.your_forums_connect))
                }
            }
        }
        ForumMenu(menu, forum, { menu = false }, onRemove) { actions.openInBrowser(forum) }
    }
}

/** The forum's logo and name; the one open says so. */
@Composable
private fun RowScope.Name(forum: YourForum) {
    ForumLogo(
        forum.name,
        size = 36.dp,
        logoUrl = forum.logoUrl,
    )
    Column(Modifier.weight(1f)) {
        Text(
            forum.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (forum.current) FontWeight.SemiBold else null,
        )
        if (forum.current) {
            Text(
                stringResource(R.string.your_forums_open),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** SH-15's long-press menu. Mark all read (Could) waits for an API to do it at once. */
@Composable
private fun ForumMenu(
    expanded: Boolean,
    forum: YourForum,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onBrowser: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (forum.held) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.accounts_remove)) },
                onClick = {
                    onDismiss()
                    onRemove()
                },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.your_forums_browser)) },
            onClick = {
                onDismiss()
                onBrowser()
            },
        )
    }
}
