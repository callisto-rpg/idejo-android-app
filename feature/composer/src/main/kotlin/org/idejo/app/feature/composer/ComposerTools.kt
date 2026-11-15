// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.idejo.app.core.designsystem.theme.onSecondaryClick

/** What the graphical composer's buttons do to the box (CO-10). */
internal class Tools(
    val value: () -> TextFieldValue,
    val onValue: (TextFieldValue) -> Unit,
    val events: ComposerEvents,
    val onEmoji: () -> Unit,
    val commanding: Commanding? = null,
)

/**
 * The graphical composer's row under the box (CO-10): the smiley and the pencil at the left,
 * the picture, GIF (while on) and **+** after them; the paper plane at the right, its menu on
 * a long-press or a right-click (CO-13 to CO-15).
 */
@Composable
internal fun Toolbar(
    tools: Tools,
    editing: Boolean,
    waiting: () -> Boolean,
) = Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    IconButton(onClick = tools.onEmoji) {
        Icon(painterResource(R.drawable.ic_mood), stringResource(R.string.composer_emoji))
    }
    tools.commanding?.let { commanding ->
        IconButton(onClick = { commanding.pickImage(false) }) {
            Icon(painterResource(R.drawable.ic_image), stringResource(R.string.composer_image))
        }
        commanding.named("gif")?.let { gif ->
            TextButton(onClick = { commanding.run(gif) }) {
                Text(stringResource(R.string.composer_gif), fontWeight = FontWeight.Bold)
            }
        }
    }
    Pencil(tools)
    tools.commanding?.let { More(it) }
    Spacer(Modifier.weight(1f))
    SendButton(tools, editing, waiting)
}

@Composable
private fun Pencil(tools: Tools) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.composer_format))
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            val change = { change: (TextFieldValue) -> TextFieldValue ->
                open = false
                tools.onValue(change(tools.value()))
            }
            Item(R.string.composer_new_line) { change { it.newLine() } }
            Item(R.string.composer_bold) { change { it.wrap(Format.Bold) } }
            Item(R.string.composer_italic) { change { it.wrap(Format.Italic) } }
            Item(R.string.composer_strike) { change { it.wrap(Format.Strike) } }
            Item(R.string.composer_link) { change { it.link() } }
            Item(R.string.composer_code) { change { it.wrap(Format.Code) } }
            Item(R.string.composer_quote) { change { value -> value.lines { "> " } } }
            Item(R.string.composer_bullets) { change { value -> value.lines { "- " } } }
            Item(R.string.composer_numbers) { change { value -> value.lines { "$it. " } } }
        }
    }
}

/** **+**: the commands, as typing `!` (CO-13). */
@Composable
private fun More(commanding: Commanding) {
    var open by remember { mutableStateOf(false) }
    val more = commanding.more
    if (more.isEmpty()) return
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_add), stringResource(R.string.composer_commands))
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            more.forEach { command ->
                DropdownMenuItem(
                    text = { Text("!${command.name}") },
                    onClick = {
                        open = false
                        commanding.run(command)
                    },
                )
            }
        }
    }
}

/**
 * The paper plane sends, like Enter; its menu: **Send**, **Send & new line**, **Send & start
 * new message** (while editing, **Save**…; within the wait, **Send & finish (the next
 * message waits for the line)**).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SendButton(
    tools: Tools,
    editing: Boolean,
    waiting: () -> Boolean,
) {
    var open by remember { mutableStateOf(false) }
    var wait by remember { mutableStateOf(false) }
    val events = tools.events
    val label = stringResource(if (editing) R.string.composer_save else R.string.composer_send)
    val menu = {
        wait = waiting()
        open = true
    }
    Box(
        Modifier
            .size(48.dp)
            .semantics { contentDescription = label }
            .onSecondaryClick(menu)
            .combinedClickable(
                interactionSource = null,
                indication = ripple(bounded = false),
                role = Role.Button,
                onLongClickLabel = stringResource(R.string.composer_send_menu),
                onLongClick = menu,
                onClick = events.onEnter,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_send), null, tint = MaterialTheme.colorScheme.primary)
        DropdownMenu(open, onDismissRequest = { open = false }) {
            val then = { action: () -> Unit ->
                open = false
                action()
            }
            Item(
                if (editing) R.string.composer_save else R.string.composer_send,
            ) { then(events.onEnter) }
            Item(if (editing) R.string.composer_save_line else R.string.composer_send_line) {
                then {
                    events.onEnter()
                    val next = tools.value().insert("\n")
                    tools.onValue(next)
                }
            }
            val complete =
                when {
                    editing -> R.string.composer_save_end
                    wait -> R.string.composer_send_finish
                    else -> R.string.composer_send_new
                }
            Item(complete) { then(events.onComplete) }
        }
    }
}

@Composable
private fun Item(
    text: Int,
    onClick: () -> Unit,
) = DropdownMenuItem(text = { Text(stringResource(text)) }, onClick = onClick)
