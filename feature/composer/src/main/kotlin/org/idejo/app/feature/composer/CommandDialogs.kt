// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.idejo.app.core.designsystem.theme.PollBarColours
import org.idejo.app.core.designsystem.theme.parseHexColor
import org.idejo.app.core.model.Command
import org.idejo.app.core.model.Emoji
import org.idejo.app.core.model.PollAnswer
import org.idejo.app.core.model.PollCreate

/** The hours a poll or a scheduled message takes (CO-13): decimals, at most 168. */
internal fun hoursOf(text: String): Double? =
    text
        .trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?.takeIf { it > 0 && it <= MAX_HOURS }

/** `!schedule`'s dialog: in how many hours. */
@Composable
internal fun ScheduleDialog(
    onSchedule: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var hours by remember { mutableStateOf("") }
    val value = hoursOf(hours)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.command_schedule_title)) },
        text = { HoursField(hours) { hours = it } },
        confirmButton = {
            TextButton(onClick = { value?.let(onSchedule) }, enabled = value != null) {
                Text(stringResource(R.string.command_schedule))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.command_cancel)) }
        },
    )
}

/** The fields a plugin's block needs (CO-13), by its block. */
internal fun pluginFields(command: Command): List<String> =
    when (command.block) {
        "video" -> listOf("link")
        "iframe" -> listOf("code")
        "button" -> listOf("label", "url")
        "card" -> listOf("label", "body")
        else -> emptyList()
    }

/** A plugin's dialog: its block's fields, [first] filled from `!command:argument:`. */
@Composable
internal fun PluginDialog(
    command: Command,
    first: String?,
    onRun: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val fields = pluginFields(command)
    val values =
        remember {
            mutableStateListOf<String>().apply {
                fields.indices.forEach { add(if (it == 0) first.orEmpty() else "") }
            }
        }
    val ready = values.all { it.isNotBlank() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("!${command.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEachIndexed { i, field ->
                    Field(values[i], FIELD_LABELS.getValue(field)) { values[i] = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onRun(fields.zip(values.map { it.trim() }).toMap()) },
                enabled = ready,
            ) {
                Text(stringResource(R.string.command_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.command_cancel)) }
        },
    )
}

/** After an image is uploaded (CO-14): its description, for those who cannot see it. */
@Composable
internal fun AltTextDialog(
    onAlt: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var alt by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.command_alt_title)) },
        text = { Field(alt, R.string.command_alt) { alt = it } },
        confirmButton = {
            TextButton(
                onClick = { onAlt(alt.trim()) },
            ) { Text(stringResource(R.string.command_save)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) { Text(stringResource(R.string.command_skip)) }
        },
    )
}

/** The image's Markdown [markdown] with [alt] as its description. */
internal fun withAlt(
    markdown: String,
    alt: String,
): String {
    if (!markdown.startsWith("![")) return markdown
    val close = markdown.indexOf("](")
    if (close < 0) return markdown
    val escaped = alt.replace("[", "\\[").replace("]", "\\]")
    return "![$escaped" + markdown.substring(close)
}

@Composable
internal fun HoursField(
    hours: String,
    onChange: (String) -> Unit,
) = OutlinedTextField(
    hours,
    onChange,
    Modifier.fillMaxWidth(),
    label = { Text(stringResource(R.string.command_hours)) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    isError = hours.isNotBlank() && hoursOf(hours) == null,
)

@Composable
internal fun Field(
    value: String,
    label: Int,
    onChange: (String) -> Unit,
) = OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = {
    Text(stringResource(label))
})

@Composable
internal fun Check(
    checked: Boolean,
    label: Int,
    onChange: (Boolean) -> Unit,
) = Row(verticalAlignment = Alignment.CenterVertically) {
    Checkbox(checked, onChange)
    Text(stringResource(label))
}

private const val MAX_HOURS = 168.0
private val FIELD_LABELS =
    mapOf(
        "link" to R.string.command_field_link,
        "code" to R.string.command_field_code,
        "label" to R.string.command_field_label,
        "url" to R.string.command_field_url,
        "body" to R.string.command_field_body,
    )
