// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.deleteaccount

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.feature.settings.R
import org.idejo.app.feature.settings.openPage
import org.idejo.app.feature.settings.parts.SectionHeader

/** **Delete account** (ST-06). [deletionUrl] null: the forum gives none, and no button. */
@Immutable
data class DeleteAccountUiState(
    val deletionUrl: String? = null,
)

/** What the member does on **Delete account**. */
sealed interface DeleteAccountEvent {
    /** Opens the forum's deletion page in a Custom Tab. */
    data class Open(
        val url: String,
    ) : DeleteAccountEvent
}

@Composable
fun DeleteAccountRoute(
    modifier: Modifier = Modifier,
    viewModel: DeleteAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DeleteAccountScreen(
        state,
        onEvent = { event ->
            when (event) {
                is DeleteAccountEvent.Open -> context.openPage(event.url)
            }
        },
        modifier,
    )
}

@Composable
fun DeleteAccountScreen(
    state: DeleteAccountUiState,
    onEvent: (DeleteAccountEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionHeader(stringResource(R.string.delete_account))
        Text(
            stringResource(R.string.delete_account_warning),
            style = IdejoTheme.typography.bodyLarge,
        )
        Text(
            stringResource(R.string.delete_account_on_web),
            Modifier.padding(top = 8.dp),
            style = IdejoTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.deletionUrl?.let { url ->
            OutlinedButton(
                onClick = { onEvent(DeleteAccountEvent.Open(url)) },
                Modifier.padding(top = 16.dp),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(stringResource(R.string.delete_account_open))
            }
        }
    }
}

@Preview
@Composable
private fun DeleteAccountScreenPreview() {
    IdejoTheme {
        DeleteAccountScreen(
            DeleteAccountUiState(
                deletionUrl = "https://garden.example.org/users/settings?tab=account",
            ),
            onEvent = {},
        )
    }
}
