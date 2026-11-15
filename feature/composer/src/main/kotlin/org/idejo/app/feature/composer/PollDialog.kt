// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.idejo.app.core.designsystem.theme.PollBarColours
import org.idejo.app.core.designsystem.theme.parseHexColor
import org.idejo.app.core.model.Emoji
import org.idejo.app.core.model.PollAnswer
import org.idejo.app.core.model.PollCreate

/** An answer being written: its emoji (drawn in the row) and bar colour. */
internal data class AnswerRow(
    val text: String = "",
    val emoji: Emoji? = null,
    val colour: String? = null,
)

/** The poll the dialog sends, or null while it is not ready: 2 answers, a question, hours. */
internal fun pollOf(
    question: String,
    rows: List<AnswerRow>,
    hours: String,
    multiple: Boolean,
    hidden: Boolean,
): PollCreate? {
    val answers =
        rows
            .filter { it.text.isNotBlank() }
            .map { PollAnswer(it.text.trim(), it.emoji?.name, it.colour) }
    val hoursValue = hoursOf(hours)
    if (question.isBlank() || answers.size < 2 || hoursValue == null) return null
    return PollCreate(question.trim(), answers, hoursValue, multiple, hidden)
}

/**
 * `!poll`'s dialog: question, 2 to 10 answers each with an emoji and a bar colour, several
 * answers, hours, hidden results.
 */
@Composable
internal fun PollDialog(
    emoji: EmojiSource,
    onPoll: (PollCreate) -> Unit,
    onDismiss: () -> Unit,
) {
    var question by remember { mutableStateOf("") }
    val answers = remember { mutableStateListOf(AnswerRow(), AnswerRow()) }
    var multiple by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(false) }
    var hours by remember { mutableStateOf("") }
    val poll = pollOf(question, answers, hours, multiple, hidden)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.command_poll_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Field(question, R.string.command_poll_question) { question = it }
                answers.forEachIndexed { i, answer ->
                    Answer(i, answer, emoji) { answers[i] = it }
                }
                if (answers.size < MAX_ANSWERS) {
                    TextButton(
                        onClick = { answers += AnswerRow() },
                    ) { Text(stringResource(R.string.command_poll_add)) }
                }
                Check(multiple, R.string.command_poll_multiple) { multiple = it }
                HoursField(hours) { hours = it }
                Check(hidden, R.string.command_poll_hidden) { hidden = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { poll?.let(onPoll) },
                enabled = poll != null,
            ) { Text(stringResource(R.string.command_poll)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.command_cancel)) }
        },
    )
}

/** Where the emoji picker finds emoji, and the forum's address for their pictures. */
internal data class EmojiSource(
    val origin: String,
    val search: suspend (String) -> List<Emoji>,
)

/** One answer: its emoji button, its text, its bar colour's menu. */
@Composable
private fun Answer(
    index: Int,
    answer: AnswerRow,
    emoji: EmojiSource,
    onChange: (AnswerRow) -> Unit,
) = Row(verticalAlignment = Alignment.CenterVertically) {
    AnswerEmoji(index, answer, emoji, onChange)
    OutlinedTextField(
        answer.text,
        { onChange(answer.copy(text = it)) },
        Modifier.weight(1f),
        label = { Text(stringResource(R.string.command_poll_answer, index + 1)) },
        singleLine = true,
    )
    AnswerColour(index, answer, onChange)
}

/** The answer's emoji: the picker, or the one chosen, which a tap takes back. */
@Composable
private fun AnswerEmoji(
    index: Int,
    answer: AnswerRow,
    emoji: EmojiSource,
    onChange: (AnswerRow) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val chosen = answer.emoji
    IconButton(onClick = {
        if (chosen ==
            null
        ) {
            picking = true
        } else {
            onChange(answer.copy(emoji = null))
        }
    }) {
        val char = chosen?.char
        val image = chosen?.imageUrl
        val label = stringResource(R.string.command_poll_emoji, index + 1)
        when {
            char != null -> Text(char, fontSize = 20.sp)
            image != null -> AsyncImage(absolute(emoji.origin, image), label, Modifier.size(24.dp))
            else -> Icon(painterResource(R.drawable.ic_mood), label)
        }
    }
    if (picking) {
        EmojiPicker(
            emoji.origin,
            emoji.search,
            onPick = {
                picking = false
                onChange(answer.copy(emoji = it))
            },
            onDismiss = { picking = false },
        )
    }
}

/** The answer's bar colour: the theme's, or one of [PollBarColours]. */
@Composable
private fun AnswerColour(
    index: Int,
    answer: AnswerRow,
    onChange: (AnswerRow) -> Unit,
) = Box {
    var open by remember { mutableStateOf(false) }
    val theme = MaterialTheme.colorScheme.primary
    IconButton(onClick = { open = true }) {
        Icon(
            painterResource(R.drawable.ic_palette),
            stringResource(R.string.command_poll_colour, index + 1),
            tint = parseHexColor(answer.colour) ?: theme,
        )
    }
    DropdownMenu(open, onDismissRequest = { open = false }) {
        val choose = { colour: String? ->
            open = false
            onChange(answer.copy(colour = colour))
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.command_poll_theme_colour)) },
            leadingIcon = { Swatch(theme) },
            onClick = { choose(null) },
        )
        PollBarColours.forEach { hex ->
            DropdownMenuItem(
                text = { Text(hex) },
                leadingIcon = { Swatch(parseHexColor(hex) ?: theme) },
                onClick = { choose(hex) },
            )
        }
    }
}

@Composable
private fun Swatch(colour: Color) = Box(Modifier.size(20.dp).background(colour, CircleShape))

private const val MAX_ANSWERS = 10
