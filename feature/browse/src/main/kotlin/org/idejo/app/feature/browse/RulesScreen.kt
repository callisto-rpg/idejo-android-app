// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.idejo.app.core.data.WelcomeState
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.markdown.IdejoMarkdown
import org.idejo.app.core.markdown.MarkdownActions
import org.idejo.app.core.markdown.MarkdownBody

/** **Rules & Roles** (BR-07); [onLink] opens a link of About or Rules. */
@Composable
fun RulesRoute(
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RulesScreen(
        state = state,
        actions = RulesActions(viewModel::tick, viewModel::save, onLink),
        onRefresh = viewModel::refresh,
        modifier = modifier,
    )
}

/** What the member does with About, Rules and their roles. */
class RulesActions(
    val onTick: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onLink: (String) -> Unit,
)

/** What this server is about, its rules, and the roles the member may give themselves. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    state: WelcomeState,
    actions: RulesActions,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(false, onRefresh, modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageTitle(stringResource(R.string.rules_title))
            val server = state.server
            when {
                state.loading -> {
                    MoreSpinner()
                }

                server == null -> {
                    state.problem?.let { ProblemRow(it, onRefresh) }
                }

                server.aboutMd.isNullOrBlank() && server.rulesMd.isNullOrBlank() &&
                    server.roles.isEmpty() -> {
                    Text(
                        stringResource(R.string.rules_nothing),
                        style = IdejoTheme.typography.bodyLarge,
                    )
                }

                else -> {
                    RulesContent(state, actions, stringResource(R.string.rules_save), page = true)
                }
            }
        }
    }
}

/**
 * About, Rules and **Add roles** in that order of reading, then [submit]: the page's **Save
 * my roles** or the welcome card's **Continue**. Roles are for members signed in.
 */
@Composable
internal fun RulesContent(
    state: WelcomeState,
    actions: RulesActions,
    submit: String,
    page: Boolean,
) {
    val server = state.server ?: return
    val markdown = remember(actions.onLink) { MarkdownActions(onLink = actions.onLink) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        server.aboutMd?.takeIf { it.isNotBlank() }?.let { about ->
            Heading(stringResource(R.string.rules_about), page)
            MarkdownBody(remember(about) { IdejoMarkdown.parse(about) }, actions = markdown)
        }
        server.rulesMd?.takeIf { it.isNotBlank() }?.let { rules ->
            Heading(stringResource(R.string.rules_rules), page)
            MarkdownBody(remember(rules) { IdejoMarkdown.parse(rules) }, actions = markdown)
        }
        if (state.me == null) return@Column
        if (server.roles.isNotEmpty()) {
            Heading(stringResource(R.string.rules_add_roles), page = false)
            Text(
                stringResource(R.string.rules_roles_hint),
                style = IdejoTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            server.roles.forEach { role ->
                val id = role.id ?: return@forEach
                RoleChoice(role.name, role.colour, id in state.chosen) { actions.onTick(id) }
            }
        }
        state.problem?.let { ProblemRow(it, actions.onSubmit) }
        Button(onClick = actions.onSubmit, enabled = !state.saving) { Text(submit) }
        if (page && state.saved) {
            Text(stringResource(R.string.rules_saved), style = IdejoTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Heading(
    text: String,
    page: Boolean,
) {
    if (page) {
        SectionTitle(text)
    } else {
        Text(
            text,
            Modifier.semantics { heading() },
            style = IdejoTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A self-select role, ticked when held, its name in the role's colour as on the web. */
@Composable
private fun RoleChoice(
    name: String,
    colour: String?,
    ticked: Boolean,
    onTick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(ticked, role = Role.Checkbox, onValueChange = { onTick() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(ticked, onCheckedChange = null, Modifier.padding(12.dp))
        Text(
            name,
            style = IdejoTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = IdejoTheme.colors.roleName(colour),
        )
    }
}
