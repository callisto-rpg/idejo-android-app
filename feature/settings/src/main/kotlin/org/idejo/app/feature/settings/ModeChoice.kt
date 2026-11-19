// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.idejo.app.core.data.AppMode
import org.idejo.app.feature.settings.parts.RadioGroupRow

/**
 * **Mode** (MD-04, ST-20): idejo ID mode or one forum. Choosing idejo ID from single-forum
 * mode is its sign-in ([onIdejoId]), which keeps the forum; leaving idejo ID mode asks which
 * forum to keep and names those the app signs out of.
 */
@Composable
internal fun ModeChoice(
    mode: AppMode,
    state: AppSettingsUiState,
    onEvent: (AppSettingsEvent) -> Unit,
    onIdejoId: () -> Unit,
) {
    var leaving by remember { mutableStateOf(false) }
    Column {
        Note(stringResource(R.string.app_mode_note))
        RadioGroupRow(
            options =
                listOf(
                    AppMode.IdejoId to stringResource(R.string.app_mode_idejo_id),
                    AppMode.Single to stringResource(R.string.app_mode_single),
                ),
            selected = mode,
            onSelect = {
                when {
                    it == mode -> Unit
                    it == AppMode.IdejoId -> onIdejoId()
                    else -> leaving = true
                }
            },
        )
    }
    if (leaving) {
        LeaveIdejoId(state, onDismiss = { leaving = false }) {
            leaving = false
            onEvent(AppSettingsEvent.LeaveIdejoId(it))
        }
    }
}

/** MD-04's confirmation: the forum kept, then everything signed out of, by name. */
@Composable
private fun LeaveIdejoId(
    state: AppSettingsUiState,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var keep by remember {
        mutableStateOf(state.current ?: state.forums.firstOrNull()?.origin)
    }
    val left =
        listOfNotNull(state.inboxName) + state.forums.filter { it.origin != keep }.map { it.name }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_mode_leave_title)) },
        text = {
            Column {
                if (state.forums.isEmpty()) {
                    Text(stringResource(R.string.app_mode_leave_none))
                } else {
                    Text(stringResource(R.string.app_mode_leave_keep))
                    RadioGroupRow(
                        options = state.forums.map { it.origin to it.name },
                        selected = keep,
                        onSelect = { keep = it },
                    )
                }
                Text(
                    stringResource(R.string.app_mode_leave_signed_out, left.joinToString(", ")),
                    Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(keep) }) {
                Text(stringResource(R.string.app_mode_leave_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.app_mode_cancel)) }
        },
    )
}
