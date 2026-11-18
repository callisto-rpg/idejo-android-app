// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.ignoredusers

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.designsystem.component.Avatar
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.designsystem.theme.parseHexColor
import org.idejo.app.core.designsystem.theme.pictureUrl
import org.idejo.app.core.model.Person
import org.idejo.app.feature.settings.R
import org.idejo.app.feature.settings.parts.ActionRow
import org.idejo.app.feature.settings.parts.ErrorState
import org.idejo.app.feature.settings.parts.LoadingState
import org.idejo.app.feature.settings.parts.ProblemText
import org.idejo.app.feature.settings.parts.SectionHeader

/**
 * **Ignored users** (ST-08): the members the member ignores (`GET /me/ignored`), each with
 * **Stop ignoring**; none, the web's "You ignore nobody.".
 */
@Immutable
data class IgnoredUsersUiState(
    /** True until the first `GET /me/ignored` answer. */
    val loading: Boolean = true,
    /** Why the section could not be read: the error state, with **Try again**. */
    val loadProblem: ApiProblem? = null,
    /** The members ignored; null until the first answer. */
    val members: List<Person>? = null,
    /** The id of the member being taken off the list: the buttons wait. */
    val saving: String? = null,
    /** Why the last **Stop ignoring** was not done; the row stays. */
    val problem: ApiProblem? = null,
    /** The forum's address, for pictures given as paths. */
    val origin: String = "",
)

/** What the member does on **Ignored users**. */
sealed interface IgnoredUsersEvent {
    data class StopIgnoring(
        val id: String,
    ) : IgnoredUsersEvent

    data object Retry : IgnoredUsersEvent
}

@Composable
fun IgnoredUsersRoute(
    modifier: Modifier = Modifier,
    viewModel: IgnoredUsersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    IgnoredUsersScreen(state, viewModel::onEvent, modifier)
}

@Composable
fun IgnoredUsersScreen(
    state: IgnoredUsersUiState,
    onEvent: (IgnoredUsersEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionHeader(stringResource(R.string.ignored_users))
        Text(
            stringResource(R.string.ignored_users_help),
            Modifier.padding(bottom = 12.dp),
            style = IdejoTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            state.loadProblem != null -> {
                ErrorState(state.loadProblem, onRetry = { onEvent(IgnoredUsersEvent.Retry) })
            }

            state.loading -> {
                LoadingState()
            }

            else -> {
                val members = state.members.orEmpty()
                if (members.isEmpty()) {
                    Text(
                        stringResource(R.string.ignored_users_none),
                        style = IdejoTheme.typography.bodyLarge,
                    )
                }
                members.forEach { person ->
                    val id = person.id ?: return@forEach
                    IgnoredRow(
                        person,
                        state.origin,
                        enabled = state.saving == null,
                        onStopIgnoring = { onEvent(IgnoredUsersEvent.StopIgnoring(id)) },
                    )
                }
                state.problem?.let { ProblemText(it) }
            }
        }
    }
}

@Composable
private fun IgnoredRow(
    person: Person,
    origin: String,
    enabled: Boolean,
    onStopIgnoring: () -> Unit,
) {
    val picture = pictureUrl(person.avatarUrl, person.avatarDarkUrl, IdejoTheme.dark)
    ActionRow(
        title = person.displayName,
        action = stringResource(R.string.ignored_users_stop),
        actionLabel = stringResource(R.string.ignored_users_stop_named, person.displayName),
        enabled = enabled,
        onAction = onStopIgnoring,
        leading = {
            Avatar(
                person.displayName,
                parseHexColor(person.colour) ?: MaterialTheme.colorScheme.tertiary,
                size = 40.dp,
                image =
                    picture?.let { url ->
                        {
                            AsyncImage(
                                if (url.startsWith("/")) origin.trimEnd('/') + url else url,
                                null,
                                Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    },
            )
        },
    )
}

@Preview
@Composable
private fun IgnoredUsersScreenPreview() {
    IdejoTheme {
        IgnoredUsersScreen(
            IgnoredUsersUiState(
                loading = false,
                members =
                    listOf(
                        Person(id = "u_leo", displayName = "Leo Moreau"),
                        Person(id = "u_mina", displayName = "Mina Kader", colour = "#6a3e8e"),
                    ),
            ),
            onEvent = {},
        )
    }
}
