// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.password

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.feature.settings.R
import org.idejo.app.feature.settings.parts.ProblemText
import org.idejo.app.feature.settings.parts.SectionHeader

/**
 * **Change password** (ST-04). The passwords live only in memory, never saved; [toString]
 * hides them so they cannot reach a log.
 */
@Immutable
data class PasswordUiState(
    val current: String = "",
    val new: String = "",
    val repeat: String = "",
    /** On its way to the server: the button waits. */
    val saving: Boolean = false,
    /** The server agreed: every device signs in again, this one too. */
    val changed: Boolean = false,
    /** Why it was not changed, in the server's words. */
    val problem: ApiProblem? = null,
) {
    override fun toString(): String = "PasswordUiState(saving=$saving, changed=$changed)"
}

/** What the member does on **Change password**. */
sealed interface PasswordEvent {
    class EditCurrent(
        val text: String,
    ) : PasswordEvent

    class EditNew(
        val text: String,
    ) : PasswordEvent

    class EditRepeat(
        val text: String,
    ) : PasswordEvent

    data object Change : PasswordEvent
}

@Composable
fun PasswordRoute(
    modifier: Modifier = Modifier,
    viewModel: PasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PasswordScreen(state, viewModel::onEvent, modifier)
}

@Composable
fun PasswordScreen(
    state: PasswordUiState,
    onEvent: (PasswordEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(stringResource(R.string.password))
        Text(
            stringResource(R.string.password_warning),
            style = IdejoTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.changed) {
            Text(
                stringResource(R.string.password_changed),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = IdejoTheme.typography.bodyLarge,
            )
            return@Column
        }
        PasswordField(state.current, R.string.password_current, ContentType.Password) {
            onEvent(PasswordEvent.EditCurrent(it))
        }
        PasswordField(state.new, R.string.password_new, ContentType.NewPassword) {
            onEvent(PasswordEvent.EditNew(it))
        }
        PasswordField(state.repeat, R.string.password_repeat, ContentType.NewPassword) {
            onEvent(PasswordEvent.EditRepeat(it))
        }
        Button(
            onClick = { onEvent(PasswordEvent.Change) },
            enabled = !state.saving && state.current.isNotEmpty() && state.new.isNotEmpty(),
        ) { Text(stringResource(R.string.password_change)) }
        state.problem?.let { ProblemText(it) }
    }
}

@Composable
private fun PasswordField(
    value: String,
    label: Int,
    type: ContentType,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        Modifier.fillMaxWidth().semantics { contentType = type },
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
            ),
    )
}

@Preview
@Composable
private fun PasswordScreenPreview() {
    IdejoTheme { PasswordScreen(PasswordUiState(current = "secret"), onEvent = {}) }
}
