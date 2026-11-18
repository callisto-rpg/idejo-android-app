// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.appsdevices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Token
import org.idejo.app.feature.settings.R
import org.idejo.app.feature.settings.parts.ActionRow
import org.idejo.app.feature.settings.parts.ErrorState
import org.idejo.app.feature.settings.parts.LoadingState
import org.idejo.app.feature.settings.parts.ProblemText
import org.idejo.app.feature.settings.parts.SectionHeader
import org.idejo.app.feature.settings.parts.day
import java.time.Instant

/**
 * **Apps and devices** (ST-05): the devices signed in to the app (`GET /me/tokens`), this
 * one marked, each with **Remove**. Removing this one signs it out of the forum.
 */
@Immutable
data class AppsDevicesUiState(
    /** True until the first `GET /me/tokens` answer. */
    val loading: Boolean = true,
    /** Why the section could not be read: the error state, with **Try again**. */
    val loadProblem: ApiProblem? = null,
    /** The devices, this one first; null until the first answer. */
    val devices: List<Token>? = null,
    /** The id of the device being removed: the buttons wait. */
    val removing: String? = null,
    /** Why the last **Remove** was not done; the row stays. */
    val problem: ApiProblem? = null,
)

/** What the member does on **Apps and devices**. */
sealed interface AppsDevicesEvent {
    /** Removes a device; this device only once the member has confirmed. */
    data class Remove(
        val id: String,
    ) : AppsDevicesEvent

    data object Retry : AppsDevicesEvent
}

@Composable
fun AppsDevicesRoute(
    modifier: Modifier = Modifier,
    viewModel: AppsDevicesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppsDevicesScreen(state, viewModel::onEvent, modifier)
}

@Composable
fun AppsDevicesScreen(
    state: AppsDevicesUiState,
    onEvent: (AppsDevicesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionHeader(stringResource(R.string.apps_devices))
        Text(
            stringResource(R.string.apps_devices_help),
            Modifier.padding(bottom = 12.dp),
            style = IdejoTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            state.loadProblem != null -> {
                ErrorState(state.loadProblem, onRetry = { onEvent(AppsDevicesEvent.Retry) })
            }

            state.loading -> {
                LoadingState()
            }

            else -> {
                val devices = state.devices.orEmpty()
                if (devices.isEmpty()) {
                    Text(
                        stringResource(R.string.apps_devices_none),
                        style = IdejoTheme.typography.bodyLarge,
                    )
                }
                devices.forEach { device ->
                    ActionRow(
                        title = device.name,
                        detail = detail(device),
                        action = stringResource(R.string.apps_devices_remove),
                        actionLabel =
                            stringResource(
                                R.string.apps_devices_remove_named,
                                device.name,
                            ),
                        enabled = state.removing == null,
                        onAction = {
                            if (device.current) {
                                confirming = device.id
                            } else {
                                onEvent(AppsDevicesEvent.Remove(device.id))
                            }
                        },
                    )
                }
                state.problem?.let { ProblemText(it) }
            }
        }
    }
    confirming?.let { id ->
        ConfirmThisDevice(
            onConfirm = {
                confirming = null
                onEvent(AppsDevicesEvent.Remove(id))
            },
            onDismiss = { confirming = null },
        )
    }
}

/** Removing this device signs it out: asked first. */
@Composable
private fun ConfirmThisDevice(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.apps_devices_this_title)) },
        text = { Text(stringResource(R.string.apps_devices_this_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.apps_devices_this_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.apps_devices_cancel)) }
        },
    )
}

/** "This device · signed in on … · last used …". */
@Composable
private fun detail(device: Token): String =
    listOfNotNull(
        stringResource(R.string.apps_devices_this).takeIf { device.current },
        device.signedInAt?.let { stringResource(R.string.apps_devices_signed_in, day(it)) },
        device.lastUsedAt?.let { stringResource(R.string.apps_devices_last_used, day(it)) },
    ).joinToString(" · ")

@Preview
@Composable
private fun AppsDevicesScreenPreview() {
    IdejoTheme {
        AppsDevicesScreen(
            AppsDevicesUiState(
                loading = false,
                devices =
                    listOf(
                        Token("t_1", "Pixel 8", signedInAt = Instant.EPOCH, current = true),
                        Token("t_2", "Galaxy Tab", signedInAt = Instant.EPOCH),
                    ),
            ),
            onEvent = {},
        )
    }
}
