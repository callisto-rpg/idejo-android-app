// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.messagecontrols

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import org.idejo.app.feature.settings.parts.SaveForAllForums
import org.idejo.app.feature.settings.parts.SectionHeader
import org.idejo.app.feature.settings.parts.SwitchRow

/**
 * **Message controls** (ST-03, MA-01): a message's actions always shown, not on a tap. A null
 * choice is one the server does not send (an older idejo): its row is hidden.
 */
@Immutable
data class MessageControlsUiState(
    /** True until the first `GET /me` answer. */
    val loading: Boolean = true,
    /** Why the section could not be read: the error state, with **Try again**. */
    val loadProblem: ApiProblem? = null,
    val onMobile: Boolean? = null,
    val onDesktop: Boolean? = null,
    /** A change is on its way to the server: the switches wait. */
    val saving: Boolean = false,
    /** Why the last change was not made; the switch is back as it was. */
    val problem: ApiProblem? = null,
)

/** What the member does on **MessageControls**. */
sealed interface MessageControlsEvent {
    data class OnMobile(
        val on: Boolean,
    ) : MessageControlsEvent

    data class OnDesktop(
        val on: Boolean,
    ) : MessageControlsEvent

    data object Retry : MessageControlsEvent
}

@Composable
fun MessageControlsRoute(
    modifier: Modifier = Modifier,
    viewModel: MessageControlsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MessageControlsScreen(state, viewModel::onEvent, modifier) { SaveForAllForums(it) }
}

@Composable
fun MessageControlsScreen(
    state: MessageControlsUiState,
    onEvent: (MessageControlsEvent) -> Unit,
    modifier: Modifier = Modifier,
    everywhere: @Composable (sections: List<String>) -> Unit = {},
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionHeader(stringResource(R.string.message_controls))
        when {
            state.loadProblem != null -> {
                ErrorState(state.loadProblem, onRetry = { onEvent(MessageControlsEvent.Retry) })
            }

            state.loading -> {
                LoadingState()
            }

            else -> {
                if (state.onMobile != null || state.onDesktop != null) {
                    Text(
                        stringResource(R.string.message_controls_help),
                        Modifier.padding(bottom = 8.dp),
                        style = IdejoTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.onMobile?.let { on ->
                    SwitchRow(
                        label = stringResource(R.string.message_controls_mobile),
                        checked = on,
                        enabled = !state.saving,
                        onCheckedChange = { onEvent(MessageControlsEvent.OnMobile(it)) },
                    )
                }
                state.onDesktop?.let { on ->
                    SwitchRow(
                        label = stringResource(R.string.message_controls_desktop),
                        checked = on,
                        enabled = !state.saving,
                        onCheckedChange = { onEvent(MessageControlsEvent.OnDesktop(it)) },
                    )
                }
                everywhere(listOf("controls"))
                state.problem?.let { ProblemText(it) }
            }
        }
    }
}

@Preview
@Composable
private fun MessageControlsScreenPreview() {
    IdejoTheme {
        MessageControlsScreen(
            MessageControlsUiState(loading = false, onMobile = true, onDesktop = false),
            onEvent = {},
        )
    }
}
