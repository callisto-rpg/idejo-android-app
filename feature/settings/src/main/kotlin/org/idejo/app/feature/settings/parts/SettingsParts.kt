// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.parts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.feature.settings.R

// The small pieces every Profile and account section is built from (CLAUDE.md → "Adding a
// settings section"). Colours and type come from the theme only.

/** A section's or a group's title, marked as a heading for TalkBack. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier.padding(top = 24.dp, bottom = 8.dp).semantics { heading() },
        style = IdejoTheme.typography.titleMedium,
    )
}

/**
 * A switch with its label and, under it, the web's helper text. The whole row toggles (a
 * 48 dp target); [enabled] false while a change is being saved.
 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    helper: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = IdejoTheme.typography.bodyLarge)
            if (helper != null) {
                Text(
                    helper,
                    style = IdejoTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}

/** One choice of several, as radio buttons: [options] are (value, label) pairs. */
@Composable
fun <T> RadioGroupRow(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth().selectableGroup()) {
        options.forEach { (value, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        value == selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(value) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(value == selected, onClick = null, enabled = enabled)
                Text(
                    label,
                    Modifier.padding(start = 16.dp),
                    style = IdejoTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/**
 * A line of a list with a button at its end (**Remove**, **Stop ignoring**…), after an
 * optional [leading] mark (a person's picture). [actionLabel] is what TalkBack hears for the
 * button when [action] alone is not enough ("Stop ignoring Leo Moreau").
 */
@Composable
fun ActionRow(
    title: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    actionLabel: String? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = IdejoTheme.typography.bodyLarge)
            if (detail != null) {
                Text(
                    detail,
                    style = IdejoTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(
            onClick = onAction,
            enabled = enabled,
            modifier =
                if (actionLabel == null) {
                    Modifier
                } else {
                    Modifier.semantics { contentDescription = actionLabel }
                },
        ) { Text(action) }
    }
}

/** A section still being read. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.settings_loading)
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = loading })
    }
}

/** A section that could not be read, with **Try again**. */
@Composable
fun ErrorState(
    problem: ApiProblem,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(problemWords(problem, change = false), style = IdejoTheme.typography.bodyLarge)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.settings_try_again)) }
    }
}

/** Why the last change was not made: shown under the rows, in the error colour. */
@Composable
fun ProblemText(
    problem: ApiProblem,
    modifier: Modifier = Modifier,
) {
    Text(
        problemWords(problem, change = true),
        modifier.padding(vertical = 8.dp),
        style = IdejoTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

/**
 * The server's own `message` when it answered; otherwise the app's words for being offline
 * (after a [change]: "nothing was changed") or for an answer it could not read.
 */
@Composable
private fun problemWords(
    problem: ApiProblem,
    change: Boolean,
): String =
    when (problem) {
        is ApiProblem.Server -> {
            problem.error.message
        }

        is ApiProblem.Network -> {
            stringResource(
                if (change) R.string.settings_offline else R.string.settings_load_offline,
            )
        }

        is ApiProblem.Malformed -> {
            stringResource(R.string.settings_unreadable)
        }
    }
