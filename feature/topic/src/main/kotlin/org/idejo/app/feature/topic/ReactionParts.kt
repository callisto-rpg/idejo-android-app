// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.idejo.app.core.designsystem.component.EmojiPicker
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Emoji
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.Reactor

/**
 * **React** (MA-03): the ten quick choices, then **+** for every emoji with search; a tap
 * adds the reaction, or takes back the member's own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReactionMenu(
    open: Boolean,
    message: Message,
    actions: MessageActions,
    origin: String,
    onClose: () -> Unit,
) {
    var searching by remember { mutableStateOf(false) }
    val react = { emoji: Emoji ->
        onClose()
        val mine = message.reactions.any { it.name == emoji.name && it.mine }
        actions.onReact?.invoke(message.id, emoji.name, !mine)
    }
    DropdownMenu(open, onDismissRequest = onClose) {
        FlowRow(Modifier.widthIn(max = 280.dp).padding(horizontal = 8.dp)) {
            actions.quick.take(QUICK).forEach { emoji ->
                EmojiButton(emoji, origin) { react(emoji) }
            }
            IconButton(onClick = {
                onClose()
                searching = true
            }) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.action_more_emoji))
            }
        }
    }
    if (searching) {
        EmojiPicker(
            actions.searchEmoji,
            onPick = {
                searching = false
                react(it)
            },
            onDismiss = { searching = false },
        ) { url -> AsyncImage(absolute(origin, url), null, Modifier.fillMaxSize()) }
    }
}

@Composable
private fun EmojiButton(
    emoji: Emoji,
    origin: String,
    onClick: () -> Unit,
) = Box(
    Modifier
        .size(44.dp)
        .semantics { contentDescription = ":${emoji.name}:" }
        .clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    val char = emoji.char
    val url = emoji.imageUrl
    when {
        char != null -> Text(char, fontSize = 22.sp)
        url != null -> AsyncImage(absolute(origin, url), null, Modifier.size(26.dp))
    }
}

/** Who reacted to a message (MA-03), five at first, then **Show more**. */
@Composable
internal fun ReactorsDialog(
    messageId: String,
    reactors: suspend (String) -> List<Reactor>,
    onDismiss: () -> Unit,
) {
    var found by remember { mutableStateOf<List<Reactor>?>(null) }
    var all by remember { mutableStateOf(false) }
    val source by rememberUpdatedState(reactors)
    LaunchedEffect(messageId) { found = source(messageId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reactors_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val list = found
                if (list == null) {
                    Text(stringResource(R.string.reactors_loading))
                } else {
                    (if (all) list else list.take(FIRST_REACTORS)).forEach { reactor ->
                        Text(
                            reactor.user.displayName + "  " + reactor.emoji.joinToString(" "),
                            style = IdejoTheme.typography.bodyLarge,
                        )
                    }
                    if (!all && list.size > FIRST_REACTORS) {
                        TextButton(onClick = { all = true }) {
                            Text(stringResource(R.string.reactors_more))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/** **Report** (MA-04): the reason, sent to the moderators; then thanks. */
@Composable
internal fun ReportDialog(
    onReport: suspend (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    var state by remember { mutableStateOf(ReportState.Writing) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_title)) },
        text = {
            when (state) {
                ReportState.Sent -> {
                    Text(stringResource(R.string.report_sent))
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            reason,
                            { reason = it },
                            Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.report_reason)) },
                            minLines = 2,
                        )
                        if (state == ReportState.Failed) {
                            Text(
                                stringResource(R.string.report_failed),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            ReportButton(state, reason, onDismiss) {
                state = ReportState.Sending
                scope.launch {
                    state = if (onReport(reason.trim())) ReportState.Sent else ReportState.Failed
                }
            }
        },
        dismissButton = {
            if (state != ReportState.Sent) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
private fun ReportButton(
    state: ReportState,
    reason: String,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
) = if (state == ReportState.Sent) {
    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
} else {
    TextButton(
        onClick = onSend,
        enabled = reason.isNotBlank() && state != ReportState.Sending,
    ) { Text(stringResource(R.string.action_report)) }
}

private enum class ReportState { Writing, Sending, Sent, Failed }

private const val QUICK = 10
private const val FIRST_REACTORS = 5
