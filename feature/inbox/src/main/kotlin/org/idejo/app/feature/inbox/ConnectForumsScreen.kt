// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.idejo.app.core.data.GroupForum
import org.idejo.app.core.designsystem.component.ForumLogo

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
 * **Connect your forums** (SI-06): each forum the member joined in the group, with its logo
 * and name, connected or not; **Connect N forums** signs in to each in a row.
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
            Modifier.safeDrawingPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
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
            items(state.forums, key = { it.url }) { forum ->
                ForumRow(
                    forum,
                    connecting = state.connecting == forum.url,
                    busy = state.connecting != null,
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
}

@Composable
private fun Heading(
    state: ConnectForumsState,
    onEvent: (ConnectForumsEvent) -> Unit,
) {
    Column(
        Modifier.widthIn(max = 560.dp).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.connect_title),
            style = MaterialTheme.typography.headlineMedium,
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
        val waiting = state.notConnected.size
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

@Composable
private fun ForumRow(
    forum: GroupForum,
    connecting: Boolean,
    busy: Boolean,
    onEvent: (ConnectForumsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(enabled = forum.connected && !busy) {
                onEvent(ConnectForumsEvent.Enter(forum))
            }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ForumLogo(
            name = forum.name,
            size = 44.dp,
            image = forum.logoUrl?.let { { AsyncImage(model = it, contentDescription = null) } },
        )
        Column(Modifier.weight(1f)) {
            Text(forum.name, style = MaterialTheme.typography.titleMedium)
            val status =
                if (forum.connected) R.string.connect_connected else R.string.connect_not_connected
            Text(
                stringResource(status),
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (forum.connected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
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
                image =
                    forum.logoUrl?.let {
                        {
                            AsyncImage(
                                model = it,
                                contentDescription = null,
                            )
                        }
                    },
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
