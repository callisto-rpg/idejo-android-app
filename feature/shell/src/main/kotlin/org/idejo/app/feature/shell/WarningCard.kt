// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.data.WarningsState
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Warning
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** What the warning card does. */
internal class WarningActions(
    val onUnderstand: () -> Unit,
    val onMessage: (String) -> Unit,
    val onRules: () -> Unit,
)

/**
 * The web's warning card (TV-11, idejo MO-02): each warning's reason, when it was given and
 * **The message**, then "Please follow the forum's rules." with **Read the rules**, until
 * **I understand**. Closing it acknowledges them too, as the web's.
 */
@Composable
internal fun WarningCard(
    state: WarningsState,
    actions: WarningActions,
) {
    AlertDialog(
        onDismissRequest = actions.onUnderstand,
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = {
            Icon(
                painterResource(R.drawable.ic_warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(
                pluralStringResource(R.plurals.warning_title, state.warnings.size),
                Modifier.semantics { heading() },
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.warnings.forEach { WarningItem(it, actions.onMessage) }
                FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.warning_follow_rules),
                        style = IdejoTheme.typography.bodySmall,
                    )
                    TextButton(onClick = actions.onRules) {
                        Text(stringResource(R.string.warning_read_rules))
                    }
                }
                state.problem?.let {
                    Text(
                        problemText(it),
                        style = IdejoTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = actions.onUnderstand, enabled = !state.sending) {
                if (state.sending) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.warning_understand))
                }
            }
        },
    )
}

@Composable
private fun WarningItem(
    warning: Warning,
    onMessage: (String) -> Unit,
) = Column {
    Text(warning.reason, style = IdejoTheme.typography.bodyLarge)
    FlowRow(itemVerticalAlignment = Alignment.CenterVertically) {
        warning.createdAt?.let {
            Text(
                WHEN.format(it),
                style = IdejoTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        warning.messageId?.let { id ->
            TextButton(onClick = { onMessage(id) }) {
                Text(stringResource(R.string.warning_message))
            }
        }
    }
}

@Composable
private fun problemText(problem: ApiProblem): String =
    when (problem) {
        is ApiProblem.Server -> problem.error.message
        is ApiProblem.Network -> stringResource(R.string.warning_offline)
        is ApiProblem.Malformed -> stringResource(R.string.warning_failed)
    }

/** As the web's: `2026-11-14 16:05 UTC`. */
private val WHEN: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)
