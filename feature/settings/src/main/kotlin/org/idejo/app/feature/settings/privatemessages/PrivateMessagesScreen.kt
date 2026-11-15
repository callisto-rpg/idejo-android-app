// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.privatemessages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.feature.settings.R
import org.idejo.app.feature.settings.parts.ErrorState
import org.idejo.app.feature.settings.parts.LoadingState
import org.idejo.app.feature.settings.parts.ProblemText
import org.idejo.app.feature.settings.parts.SectionHeader
import org.idejo.app.feature.settings.parts.SwitchRow

/**
 * **Private messages** (PC-03): "Members can send me private messages". Null when the server
 * does not send the choice: the row is hidden rather than showing a guess.
 */
@Immutable
data class PrivateMessagesUiState(
    /** True until the first `GET /me` answer. */
    val loading: Boolean = true,
    /** Why the section could not be read: the error state, with **Try again**. */
    val loadProblem: ApiProblem? = null,
    val acceptsPrivateMessages: Boolean? = null,
    /** A change is on its way to the server: the switches wait. */
    val saving: Boolean = false,
    /** Why the last change was not made; the switch is back as it was. */
    val problem: ApiProblem? = null,
)

/** What the member does on **Private messages**. */
sealed interface PrivateMessagesEvent {
    data class Accept(
        val on: Boolean,
    ) : PrivateMessagesEvent

    data object Retry : PrivateMessagesEvent
}

@Composable
fun PrivateMessagesRoute(
    modifier: Modifier = Modifier,
    viewModel: PrivateMessagesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PrivateMessagesScreen(state, viewModel::onEvent, modifier)
}

@Composable
fun PrivateMessagesScreen(
    state: PrivateMessagesUiState,
    onEvent: (PrivateMessagesEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionHeader(stringResource(R.string.private_messages))
        when {
            state.loadProblem != null -> {
                ErrorState(state.loadProblem, onRetry = { onEvent(PrivateMessagesEvent.Retry) })
            }

            state.loading -> {
                LoadingState()
            }

            else -> {
                state.acceptsPrivateMessages?.let { on ->
                    SwitchRow(
                        label = stringResource(R.string.private_messages_accept),
                        helper = stringResource(R.string.private_messages_accept_help),
                        checked = on,
                        enabled = !state.saving,
                        onCheckedChange = { onEvent(PrivateMessagesEvent.Accept(it)) },
                    )
                }
                state.problem?.let { ProblemText(it) }
            }
        }
    }
}

@Preview
@Composable
private fun PrivateMessagesScreenPreview() {
    IdejoTheme {
        PrivateMessagesScreen(
            PrivateMessagesUiState(loading = false, acceptsPrivateMessages = true),
            onEvent = {},
        )
    }
}
