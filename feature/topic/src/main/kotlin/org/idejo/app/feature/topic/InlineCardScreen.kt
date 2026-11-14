// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.data.InlineCard

/** Where an inline link's card leads (TV-22). Each closes the card first. */
data class InlineCardActions(
    val onTopic: (String) -> Unit = {},
    val onCategory: (String) -> Unit = {},
    val onTag: (String) -> Unit = {},
    val onUser: (String) -> Unit = {},
    val onFollow: (Boolean) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onClose: () -> Unit = {},
)

/** An inline link's card (TV-22) in its dialog; the route names the mention. */
@Composable
fun InlineCardRoute(
    onTopic: (String) -> Unit,
    onCategory: (String) -> Unit,
    onTag: (String) -> Unit,
    onUser: (String) -> Unit,
    onClose: () -> Unit,
    viewModel: InlineCardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InlineCardScreen(
        state,
        InlineCardActions(
            onTopic = onTopic,
            onCategory = onCategory,
            onTag = onTag,
            onUser = onUser,
            onFollow = viewModel::follow,
            onRetry = viewModel::load,
            onClose = onClose,
        ),
    )
}

/**
 * The web's small card for an inline link (TV-22): what it names, **Open** where the app has
 * the page, **Follow** / **Unfollow** on a topic's. A role's has no **Open** and a category's
 * no **New topic** in M1; a forum's **Join** and **Open** wait for idejo ID mode (M4)
 * (steward decisions, 2026-11-14).
 */
@Composable
fun InlineCardScreen(
    state: InlineCardUi,
    actions: InlineCardActions,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val card = state.card
            when {
                card != null -> Card(card, state, actions)
                state.loading -> CardSpinner()
                else -> state.problem?.let { ProblemRow(it, actions.onRetry) }
            }
            TextButton(onClick = actions.onClose, Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.card_close))
            }
        }
    }
}

@Composable
private fun Card(
    card: InlineCard,
    state: InlineCardUi,
    actions: InlineCardActions,
) = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    when (card) {
        is InlineCard.Topic -> TopicCard(card, state, actions)
        is InlineCard.Category -> CategoryCard(card, actions)
        is InlineCard.Tag -> TagCard(card, actions)
        is InlineCard.Role -> RoleCard(card, state.origin, actions)
        is InlineCard.Forum -> ForumCard(card.name, card.host, logo = null, state.origin)
    }
    if (state.card is InlineCard.Topic) state.problem?.let { ProblemRow(it, actions.onRetry) }
}

/** The button that opens what the card names, as the web's. */
@Composable
internal fun OpenRow(
    label: String,
    onOpen: () -> Unit,
    extra: @Composable () -> Unit = {},
) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    androidx.compose.material3.Button(onClick = onOpen) { Text(label) }
    extra()
}

/** A failed load or follow, with **Try again**. */
@Composable
internal fun ProblemRow(
    problem: ApiProblem,
    onRetry: () -> Unit,
) = Row(verticalAlignment = Alignment.CenterVertically) {
    Text(problemText(problem), Modifier.weight(1f, fill = false))
    TextButton(onClick = onRetry) { Text(stringResource(R.string.topic_retry)) }
}

@Composable
internal fun CardSpinner() = CircularProgressIndicator(Modifier.padding(8.dp))
