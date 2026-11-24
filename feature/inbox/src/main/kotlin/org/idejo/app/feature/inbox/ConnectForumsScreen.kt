// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.idejo.app.core.data.GroupForum
import org.idejo.app.core.designsystem.component.ForumLogo
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.designsystem.R as DesignR

/** idejo ID mode's inbox (MD-02): for now, the group's forums to connect and open (SI-06). */
@Composable
fun ConnectForumsRoute(
    onFindMore: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectForumsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SignInTabsEffect(
        viewModel.tabs,
        busy = state.connecting != null,
        onClose = viewModel::tabClosed,
    )
    ConnectForumsScreen(state, viewModel::onEvent, modifier, onFindMore)
}

/**
 * The inbox's **Forums** (IB-02, SI-06): a card per forum the member joined in the group,
 * with its logo, name and "N new messages"; one not connected with **Connect**, and
 * **Connect N forums** to sign in to each in a row; then **Find more forums** (SI-08).
 */
@Composable
fun ConnectForumsScreen(
    state: ConnectForumsState,
    onEvent: (ConnectForumsEvent) -> Unit,
    modifier: Modifier = Modifier,
    onFindMore: () -> Unit = {},
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            Modifier.padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Heading(state, onEvent) }
            items(state.cards, key = { "card " + it.url }) { forum ->
                JoinedCard(
                    forum,
                    connecting = state.connecting == forum.url,
                    enabled = state.connecting == null,
                    onConnect = { onEvent(ConnectForumsEvent.Connect(forum)) },
                    modifier = Modifier.widthIn(max = 560.dp).padding(bottom = 16.dp),
                )
            }
            if (state.actionFailed) {
                item {
                    Text(
                        stringResource(R.string.forum_menu_failed),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 560.dp).padding(vertical = 8.dp),
                    )
                }
            }
            items(state.forums, key = { it.url }) { forum ->
                ForumRow(
                    forum,
                    unread = state.unread[forum.url] ?: 0,
                    connecting = state.connecting == forum.url,
                    busy = state.connecting != null,
                    menu = state.menu,
                    onEvent = onEvent,
                    modifier = Modifier.widthIn(max = 560.dp),
                )
            }
            if (state.directory) {
                item {
                    OutlinedButton(
                        onClick = onFindMore,
                        enabled = state.connecting == null,
                        modifier =
                            Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(vertical = 24.dp),
                    ) { Text(stringResource(R.string.find_more)) }
                }
            }
        }
    }
    state.leaving?.let { LeaveDialog(it, onEvent) }
}

/** "Leave {name}?", as the web asks: the forum keeps the account and messages. */
@Composable
private fun LeaveDialog(
    forum: GroupForum,
    onEvent: (ConnectForumsEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(ConnectForumsEvent.ConfirmLeave(false)) },
        title = { Text(stringResource(R.string.forum_menu_leave_title, forum.name)) },
        text = { Text(stringResource(R.string.forum_menu_leave_text)) },
        confirmButton = {
            TextButton(onClick = { onEvent(ConnectForumsEvent.ConfirmLeave(true)) }) {
                Text(stringResource(R.string.forum_menu_leave))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ConnectForumsEvent.ConfirmLeave(false)) }) {
                Text(stringResource(R.string.forum_menu_cancel))
            }
        },
    )
}

@Composable
private fun Heading(
    state: ConnectForumsState,
    onEvent: (ConnectForumsEvent) -> Unit,
) {
    val waiting = state.notConnected.size
    val quiet = !state.loading && !state.failed && state.forums.isNotEmpty() && waiting == 0
    // Every forum connected: the cards say it all.
    if (quiet) return
    Column(
        Modifier.widthIn(max = 560.dp).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.connect_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        val text =
            when {
                state.loading -> stringResource(R.string.connect_loading)
                state.failed -> stringResource(R.string.connect_failed, state.inboxName)
                state.forums.isEmpty() -> stringResource(R.string.connect_none, state.inboxName)
                else -> stringResource(R.string.connect_text)
            }
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        when {
            state.loading -> {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }

            state.failed || state.forums.isEmpty() -> {
                OutlinedButton(onClick = { onEvent(ConnectForumsEvent.Refresh) }) {
                    Text(stringResource(R.string.connect_try_again))
                }
            }

            waiting > 0 -> {
                Button(
                    onClick = { onEvent(ConnectForumsEvent.ConnectAll) },
                    enabled = state.connecting == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(pluralStringResource(R.plurals.connect_all, waiting, waiting)) }
            }
        }
    }
}

/**
 * A forum's card. A connected one opens on a tap; one not connected is no link yet, and not
 * faded either: **Connect** is its action. Where the inbox answers it, a long-press (or a
 * right-click) opens the card's menu (steward request 2026-11-24).
 */
@Composable
private fun ForumRow(
    forum: GroupForum,
    unread: Int,
    connecting: Boolean,
    busy: Boolean,
    menu: Boolean,
    onEvent: (ConnectForumsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val more = stringResource(R.string.forum_menu_more)
    val enter = { onEvent(ConnectForumsEvent.Enter(forum)) }
    val shape = CardDefaults.outlinedShape
    Box(modifier) {
        OutlinedCard(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .combinedClickable(
                    enabled = !busy && (forum.connected || menu),
                    role = Role.Button,
                    onLongClickLabel = more.takeIf { menu },
                    onLongClick = { open = true }.takeIf { menu },
                    onClick = { if (forum.connected) enter() },
                ),
            shape = shape,
        ) { ForumCardContent(forum, unread, connecting, busy, onEvent) }
        if (menu) ForumMenu(open, forum, { open = false }, onEvent)
    }
}

/** The card's long-press menu: the web's Forums tab menu, in its words. */
@Composable
private fun ForumMenu(
    expanded: Boolean,
    forum: GroupForum,
    onDismiss: () -> Unit,
    onEvent: (ConnectForumsEvent) -> Unit,
) {
    val favourite = forum.favourite == true
    val label = if (favourite) R.string.forum_menu_unfavourite else R.string.forum_menu_favourite
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(label)) },
            onClick = {
                onDismiss()
                onEvent(ConnectForumsEvent.Favourite(forum, !favourite))
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.forum_menu_leave_forum)) },
            onClick = {
                onDismiss()
                onEvent(ConnectForumsEvent.Leave(forum))
            },
        )
    }
}

@Composable
private fun ForumCardContent(
    forum: GroupForum,
    unread: Int,
    connecting: Boolean,
    busy: Boolean,
    onEvent: (ConnectForumsEvent) -> Unit,
) {
    Row(
        Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ForumLogo(
            name = forum.name,
            size = 44.dp,
            logoUrl = forum.logoUrl,
        )
        Column(Modifier.weight(1f)) {
            Text(forum.name, style = MaterialTheme.typography.titleMedium)
            val status =
                when {
                    !forum.connected -> stringResource(R.string.connect_not_connected)
                    unread > 0 -> pluralStringResource(R.plurals.inbox_new_messages, unread, unread)
                    else -> null
                }
            if (status != null) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (forum.connected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
        if (forum.favourite == true) FavouriteHeart()
        when {
            connecting -> {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }

            !forum.connected -> {
                TextButton(
                    onClick = { onEvent(ConnectForumsEvent.Connect(forum)) },
                    enabled = !busy,
                ) {
                    Text(stringResource(R.string.connect_one))
                }
            }
        }
    }
}

/** A favourite forum's small red heart, at the card's right (as the web's Forums tab). */
@Composable
private fun FavouriteHeart() {
    Icon(
        painterResource(DesignR.drawable.ic_favourite),
        contentDescription = stringResource(R.string.forum_menu_is_favourite),
        tint = IdejoTheme.colors.favourite,
        modifier = Modifier.size(16.dp),
    )
}

/** SI-07: a forum joined since last seen, waiting to be connected. */
@Composable
private fun JoinedCard(
    forum: GroupForum,
    connecting: Boolean,
    enabled: Boolean,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ForumLogo(
                name = forum.name,
                size = 44.dp,
                logoUrl = forum.logoUrl,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.connect_joined, forum.name),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.connect_joined_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (connecting) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Button(onClick = onConnect, enabled = enabled) {
                    Text(stringResource(R.string.connect_one))
                }
            }
        }
    }
}
