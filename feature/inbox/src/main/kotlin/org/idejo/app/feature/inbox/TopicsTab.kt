// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.idejo.app.core.data.ForumTopic
import org.idejo.app.core.data.TopicView
import org.idejo.app.core.designsystem.component.ForumLogo
import org.idejo.app.core.designsystem.component.TopicItem

/**
 * **Topics** (IB-03): every connected forum's followed topics, the latest activity first,
 * each led by its forum's logo, with its new messages, mentions and reactions; the toolbar's
 * Compact/Expanded, **Unread only** and **Filter** by forum are remembered.
 */
@Composable
fun TopicsTab(
    state: InboxState,
    onEvent: (InboxEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Toolbar(state, onEvent) }
        items(state.topics.unreachable) { name ->
            Text(
                stringResource(R.string.inbox_unreachable, name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        val shown = state.shown
        items(shown, key = { it.origin + it.topic.id }) { item ->
            InboxTopicRow(
                item,
                expanded = state.choices.view == TopicView.Expanded,
                onClick = { onEvent(InboxEvent.Open(item)) },
            )
        }
        if (shown.isEmpty() && !state.topics.loading) {
            item {
                Text(
                    stringResource(
                        if (state.choices.unreadOnly) {
                            R.string.inbox_none_unread
                        } else {
                            R.string.inbox_none
                        },
                    ),
                    Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Toolbar(
    state: InboxState,
    onEvent: (InboxEvent) -> Unit,
) {
    var viewMenu by remember { mutableStateOf(false) }
    var filtering by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = { viewMenu = true }) {
            Text(stringResource(viewName(state.choices.view)) + " ▾")
            DropdownMenu(viewMenu, onDismissRequest = { viewMenu = false }) {
                TopicView.entries.forEach { view ->
                    DropdownMenuItem(
                        text = { Text(stringResource(viewName(view))) },
                        onClick = {
                            viewMenu = false
                            onEvent(InboxEvent.View(view))
                        },
                    )
                }
            }
        }
        FilterChip(
            selected = state.choices.unreadOnly,
            onClick = { onEvent(InboxEvent.UnreadOnly(!state.choices.unreadOnly)) },
            label = { Text(stringResource(R.string.inbox_unread_only)) },
        )
        TextButton(onClick = { filtering = true }, enabled = state.forums.isNotEmpty()) {
            Text(stringResource(R.string.inbox_filter))
        }
    }
    if (filtering) {
        ForumFilter(state, onDone = { filtering = false }, onEvent = onEvent)
    }
}

/** **Filter** by forum (IB-03): one box per connected forum. */
@Composable
private fun ForumFilter(
    state: InboxState,
    onDone: () -> Unit,
    onEvent: (InboxEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDone,
        confirmButton = {
            TextButton(onClick = onDone) { Text(stringResource(R.string.inbox_filter_done)) }
        },
        title = { Text(stringResource(R.string.inbox_filter_title)) },
        text = {
            Column {
                state.forums.forEach { (origin, name) ->
                    val shown = origin !in state.choices.hidden
                    val hidden = state.choices.hidden
                    Row(
                        Modifier.fillMaxWidth().toggleable(shown, role = Role.Checkbox) {
                            onEvent(InboxEvent.Hidden(if (it) hidden - origin else hidden + origin))
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = shown, onCheckedChange = null)
                        Text(name, Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                    }
                }
            }
        },
    )
}

/**
 * One followed topic (IB-03): idejo's row led by the forum's logo; its counts at its right,
 * or under it on narrow screens.
 */
@Composable
private fun InboxTopicRow(
    item: ForumTopic,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= WIDE
        val row: @Composable (Modifier) -> Unit = { rowModifier ->
            TopicItem(
                item.topic,
                onClick = onClick,
                modifier = rowModifier,
                expanded = expanded,
                picture = { url, pictureModifier -> AsyncImage(url, null, pictureModifier) },
                lead = {
                    ForumLogo(
                        item.forum,
                        size = 30.dp,
                        image =
                            item.logoUrl?.let {
                                {
                                    AsyncImage(
                                        it,
                                        null,
                                        Modifier.fillMaxSize(),
                                    )
                                }
                            },
                    )
                },
                starter =
                    item.forum + " · " +
                        item.topic.author
                            ?.displayName
                            .orEmpty(),
                showUnread = false,
            )
        }
        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                row(Modifier.weight(1f))
                // The column kept for every row, so the rows line up.
                Box(Modifier.width(COUNTS).padding(start = 12.dp)) { Counts(item, Alignment.Start) }
            }
        } else {
            Column {
                row(Modifier)
                Counts(item, Alignment.End, Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp))
            }
        }
    }
}

/** "N new messages", "N mentions", "N reactions", each only above 0 (IB-03). */
@Composable
private fun Counts(
    item: ForumTopic,
    alignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
) {
    val topic = item.topic
    val counts =
        listOfNotNull(
            topic.unreadCount.takeIf { it > 0 }?.let {
                pluralStringResource(R.plurals.inbox_new_messages, it, it)
            },
            topic.mentionsCount.takeIf { it > 0 }?.let {
                pluralStringResource(R.plurals.inbox_mentions, it, it)
            },
            topic.reactionsCount.takeIf { it > 0 }?.let {
                pluralStringResource(R.plurals.inbox_reactions, it, it)
            },
        )
    if (counts.isEmpty()) return
    Column(modifier.widthIn(min = 0.dp), horizontalAlignment = alignment) {
        counts.forEach {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun viewName(view: TopicView): Int =
    when (view) {
        TopicView.Compact -> R.string.inbox_view_compact
        TopicView.Expanded -> R.string.inbox_view_expanded
    }

private val WIDE = 600.dp
private val COUNTS = 140.dp
