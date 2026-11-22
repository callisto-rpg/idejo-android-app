// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.idejo.app.core.data.MemberCard
import org.idejo.app.core.data.WelcomeState
import org.idejo.app.core.designsystem.theme.IdejoTheme

/**
 * The card over the forum, as the web's: **Welcome to …** on a new member's first visit
 * until **Continue** (BR-07).
 */
@Composable
fun MemberCards(
    onLink: (String) -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MemberCards(
        state = state,
        forumName = viewModel.forumName,
        rules = RulesActions(viewModel::tick, viewModel::continueWelcome, onLink),
    )
}

/** The card [state] says is due, if any. */
@Composable
fun MemberCards(
    state: WelcomeState,
    forumName: String,
    rules: RulesActions,
) {
    val card = state.card ?: return
    // Nothing closes the cards but their buttons, as on the web.
    Dialog(
        onDismissRequest = {},
        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
    ) { MemberCardContent(card, state, forumName, rules) }
}

/** What [card] shows, without its dialog. */
@Composable
internal fun MemberCardContent(
    card: MemberCard,
    state: WelcomeState,
    forumName: String,
    rules: RulesActions,
) {
    Card {
        when (card) {
            MemberCard.Welcome -> {
                CardTitle(stringResource(R.string.welcome_title, forumName))
                RulesContent(state, rules, stringResource(R.string.welcome_continue), page = false)
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, Modifier.semantics { heading() }, style = IdejoTheme.typography.headlineSmall)
}
