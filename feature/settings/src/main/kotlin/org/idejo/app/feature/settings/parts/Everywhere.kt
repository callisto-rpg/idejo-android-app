// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.parts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.feature.settings.R
import javax.inject.Inject

/** What the last **Save for all forums** did (ST-07, IX-17). */
sealed interface EverywhereOutcome {
    /** Every forum took it: [count] of them, none at all when the member joined no other. */
    data class Saved(
        val count: Int,
    ) : EverywhereOutcome

    /** These forums did not answer or did not take a section. */
    data class Missed(
        val forums: List<String>,
    ) : EverywhereOutcome

    /** The group's service did not answer (`unavailable`), or the app was offline. */
    data object Unreachable : EverywhereOutcome
}

@Immutable
data class EverywhereUiState(
    /** The inbox's `GET /me` says `everywhere`: the button shows. */
    val offered: Boolean = false,
    val saving: Boolean = false,
    val outcome: EverywhereOutcome? = null,
)

/**
 * **Save for all forums** on the inbox's Profile and account (ST-07, IX-17): what a section
 * holds on the inbox, sent to every forum of the group the member joined, as the web's button
 * beside each Save. The app saves each change at once, so the button sits under the section.
 */
@HiltViewModel
class EverywhereViewModel
    @Inject
    constructor(
        sessions: SessionManager,
    ) : ViewModel() {
        private val session = sessions.active
        private val work = MutableStateFlow(EverywhereUiState())

        val state: StateFlow<EverywhereUiState> =
            combine(work, session?.me?.state ?: flowOf(null)) { state, me ->
                state.copy(offered = me?.everywhere == true)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, EverywhereUiState())

        /** Sends [sections] (idejo's names: `writing`, `email`…) to every forum. */
        fun save(sections: List<String>) {
            val forum = session ?: return
            if (work.value.saving) return
            work.update { it.copy(saving = true, outcome = null) }
            viewModelScope.launch {
                val outcome =
                    when (val result = forum.api.saveEverywhere(sections)) {
                        is ApiResult.Success -> {
                            val missed = result.value.missed
                            if (missed.isEmpty()) {
                                EverywhereOutcome.Saved(result.value.saved.size)
                            } else {
                                EverywhereOutcome.Missed(missed)
                            }
                        }

                        is ApiResult.Failure -> {
                            EverywhereOutcome.Unreachable
                        }
                    }
                work.update { it.copy(saving = false, outcome = outcome) }
            }
        }
    }

/**
 * Under a section of the inbox's Profile and account: **Save for all forums** and what it
 * did. Nothing shows where the inbox does not offer it (a forum, or an older idejo).
 */
@Composable
fun SaveForAllForums(
    sections: List<String>,
    modifier: Modifier = Modifier,
    viewModel: EverywhereViewModel = hiltViewModel(key = "everywhere " + sections.joinToString()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SaveForAllForums(state, onSave = { viewModel.save(sections) }, modifier)
}

@Composable
fun SaveForAllForums(
    state: EverywhereUiState,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.offered) return
    Column(modifier.padding(vertical = 8.dp)) {
        OutlinedButton(onClick = onSave, enabled = !state.saving) {
            Text(stringResource(R.string.settings_save_everywhere))
        }
        state.outcome?.let { outcome ->
            val colours = MaterialTheme.colorScheme
            val saved = outcome is EverywhereOutcome.Saved
            Text(
                outcomeWords(outcome),
                Modifier.padding(top = 8.dp),
                style = IdejoTheme.typography.bodyMedium,
                color = if (saved) colours.onSurface else colours.error,
            )
        }
    }
}

@Composable
private fun outcomeWords(outcome: EverywhereOutcome): String =
    when (outcome) {
        is EverywhereOutcome.Saved -> {
            if (outcome.count == 0) {
                stringResource(R.string.settings_everywhere_none)
            } else {
                pluralStringResource(
                    R.plurals.settings_everywhere_saved,
                    outcome.count,
                    outcome.count,
                )
            }
        }

        is EverywhereOutcome.Missed -> {
            stringResource(R.string.settings_everywhere_missed, outcome.forums.joinToString(", "))
        }

        EverywhereOutcome.Unreachable -> {
            stringResource(R.string.settings_everywhere_unreachable)
        }
    }
