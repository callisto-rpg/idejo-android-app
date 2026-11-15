// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import org.idejo.app.core.api.getOrNull
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.ForwardTarget
import javax.inject.Inject

/**
 * The forward card (CO-17): the topics and conversations the member may forward message
 * `messageId` to, from topic `topicId` (left out), the ones read last first, or matching
 * the search (`GET /forward-targets`).
 */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ForwardViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        sessions: SessionManager,
    ) : ViewModel() {
        private val session = sessions.active
        private val except: String? = savedState["topicId"]
        private val mutableQuery = MutableStateFlow("")
        val query: StateFlow<String> = mutableQuery.asStateFlow()

        val targets: StateFlow<List<ForwardTarget>> =
            mutableQuery
                .debounce(SEARCH_DELAY_MILLIS)
                .mapLatest { q ->
                    session
                        ?.call {
                            forwardTargets(
                                q.ifBlank { null },
                                except,
                            )
                        }?.getOrNull()
                        .orEmpty()
                }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        fun search(text: String) {
            mutableQuery.value = text
        }
    }

/** The card's route; [onPick] goes to the chosen target with the message (CO-17). */
@Composable
fun ForwardRoute(
    onPick: (ForwardTarget) -> Unit,
    modifier: Modifier = Modifier,
    /** The card's heading: **Forward**, or **Share to** for shared pictures (CO-14). */
    @StringRes title: Int = R.string.forward_title,
    viewModel: ForwardViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val targets by viewModel.targets.collectAsStateWithLifecycle()
    ForwardCard(query, targets, viewModel::search, onPick, modifier, title)
}

@Composable
fun ForwardCard(
    query: String,
    targets: List<ForwardTarget>,
    onSearch: (String) -> Unit,
    onPick: (ForwardTarget) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes title: Int = R.string.forward_title,
) = Surface(modifier, shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
    Column(Modifier.padding(16.dp)) {
        Text(
            stringResource(title),
            Modifier.semantics { heading() },
            style = IdejoTheme.typography.titleMedium,
        )
        OutlinedTextField(
            query,
            onSearch,
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            label = { Text(stringResource(R.string.forward_search)) },
            singleLine = true,
        )
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(targets, key = { it.id }) { target ->
                Text(
                    target.title,
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(target) }
                        .heightIn(min = 48.dp)
                        .padding(vertical = 12.dp),
                    style = IdejoTheme.typography.bodyLarge,
                )
            }
        }
    }
}

private const val SEARCH_DELAY_MILLIS = 300L
