// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.idejo.app.core.designsystem.component.ForumLogo
import org.idejo.app.core.model.DirectoryForum

@Composable
fun FindForumsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FindForumsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SignInTabsEffect(viewModel.tabs, busy = state.joining != null, onClose = viewModel::tabClosed)
    FindForumsScreen(state, viewModel::onEvent, onBack, modifier)
}

/**
 * **Find more forums** (SI-08): the group's directory, searchable, each forum with its logo,
 * name, address and members; **Join** signs in there, **Open** a forum the app holds.
 */
@Composable
fun FindForumsScreen(
    state: FindForumsState,
    onEvent: (FindForumsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            Modifier.safeDrawingPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Search(state, onEvent, onBack, Modifier.widthIn(max = 560.dp))
            }
            items(state.forums, key = { it.url }) { forum ->
                ForumCard(
                    forum,
                    held = forum.host in state.held,
                    joining = state.joining == forum.host,
                    enabled = state.joining == null,
                    onEvent = onEvent,
                    modifier = Modifier.widthIn(max = 560.dp),
                )
            }
            item { Foot(state, onEvent) }
        }
    }
}

@Composable
private fun Search(
    state: FindForumsState,
    onEvent: (FindForumsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(top = 16.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.find_back)) }
        Text(stringResource(R.string.find_more), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = { onEvent(FindForumsEvent.Search(it)) },
            label = { Text(stringResource(R.string.find_search)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ForumCard(
    forum: DirectoryForum,
    held: Boolean,
    joining: Boolean,
    enabled: Boolean,
    onEvent: (FindForumsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier.fillMaxWidth()) {
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
                Text(forum.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    forum.host,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(R.plurals.find_members, forum.members, forum.members),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                joining -> {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }

                held -> {
                    OutlinedButton(
                        onClick = { onEvent(FindForumsEvent.Open(forum)) },
                        enabled = enabled,
                    ) {
                        Text(stringResource(R.string.find_open))
                    }
                }

                else -> {
                    Button(onClick = { onEvent(FindForumsEvent.Join(forum)) }, enabled = enabled) {
                        Text(stringResource(R.string.find_join))
                    }
                }
            }
        }
    }
}

@Composable
private fun Foot(
    state: FindForumsState,
    onEvent: (FindForumsEvent) -> Unit,
) {
    Column(
        Modifier.widthIn(max = 560.dp).padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            state.loading -> {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }

            state.failed -> {
                Text(stringResource(R.string.find_failed), textAlign = TextAlign.Center)
                TextButton(onClick = { onEvent(FindForumsEvent.TryAgain) }) {
                    Text(stringResource(R.string.connect_try_again))
                }
            }

            state.forums.isEmpty() -> {
                Text(
                    stringResource(R.string.find_none, state.query),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.more -> {
                OutlinedButton(onClick = { onEvent(FindForumsEvent.More) }) {
                    Text(stringResource(R.string.find_load_more))
                }
            }
        }
    }
}
