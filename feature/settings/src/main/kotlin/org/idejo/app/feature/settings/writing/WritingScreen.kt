// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.writing

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
 * **Writing** (ST-02). A null choice is one the server does not send (an older idejo): its
 * row is hidden rather than showing a guess.
 */
@Immutable
data class WritingUiState(
    /** True until the first `GET /me` answer. */
    val loading: Boolean = true,
    /** Why the section could not be read: the error state, with **Try again**. */
    val loadProblem: ApiProblem? = null,
    val autoPunctuation: Boolean? = null,
    val graphicalComposer: Boolean? = null,
    /** A change is on its way to the server: the switches wait. */
    val saving: Boolean = false,
    /** Why the last change was not made; the switch is back as it was. */
    val problem: ApiProblem? = null,
)

/** What the member does on **Writing**. */
sealed interface WritingEvent {
    data class AutoPunctuation(
        val on: Boolean,
    ) : WritingEvent

    data class GraphicalComposer(
        val on: Boolean,
    ) : WritingEvent

    data object Retry : WritingEvent
}

@Composable
fun WritingRoute(
    modifier: Modifier = Modifier,
    viewModel: WritingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WritingScreen(state, viewModel::onEvent, modifier)
}

@Composable
fun WritingScreen(
    state: WritingUiState,
    onEvent: (WritingEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionHeader(stringResource(R.string.writing))
        when {
            state.loadProblem != null -> {
                ErrorState(state.loadProblem, onRetry = { onEvent(WritingEvent.Retry) })
            }

            state.loading -> {
                LoadingState()
            }

            else -> {
                state.autoPunctuation?.let { on ->
                    SwitchRow(
                        label = stringResource(R.string.writing_auto_punctuation),
                        helper = stringResource(R.string.writing_auto_punctuation_help),
                        checked = on,
                        enabled = !state.saving,
                        onCheckedChange = { onEvent(WritingEvent.AutoPunctuation(it)) },
                    )
                }
                state.graphicalComposer?.let { on ->
                    SwitchRow(
                        label = stringResource(R.string.writing_graphical_composer),
                        helper = stringResource(R.string.writing_graphical_composer_help),
                        checked = on,
                        enabled = !state.saving,
                        onCheckedChange = { onEvent(WritingEvent.GraphicalComposer(it)) },
                    )
                }
                state.problem?.let { ProblemText(it) }
            }
        }
    }
}

@Preview
@Composable
private fun WritingScreenPreview() {
    IdejoTheme {
        WritingScreen(
            WritingUiState(loading = false, autoPunctuation = true, graphicalComposer = false),
            onEvent = {},
        )
    }
}
