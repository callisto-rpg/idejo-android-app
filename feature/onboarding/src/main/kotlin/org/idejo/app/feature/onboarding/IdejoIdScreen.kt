// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.idejo.app.core.data.SignInState
import org.idejo.app.core.designsystem.component.ForumLogo

@Composable
internal fun IdejoIdRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IdejoIdViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signIn by viewModel.signInState.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    var noBrowser by remember { mutableStateOf(false) }
    var leftForTab by remember { mutableStateOf(false) }
    LaunchedEffect(tab) {
        val url = tab ?: return@LaunchedEffect
        viewModel.tabOpened()
        if (!context.openSignIn(url)) {
            viewModel.tabClosed()
            noBrowser = true
        }
    }
    // Back from the Custom Tab without an answer: the member closed it (SI-03).
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { leftForTab = signIn is SignInState.Waiting }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (leftForTab) viewModel.tabClosed()
        leftForTab = false
    }
    IdejoIdScreen(state, signIn, viewModel::signIn, onBack, modifier)
    SignInFailure(signIn, noBrowser) {
        noBrowser = false
        viewModel.dismissFailure()
    }
}

/**
 * **Sign in with idejo ID** (SI-06, MD-02): one button for idejo's own group; under "Use
 * another idejo group", the address of another group's inbox.
 */
@Composable
fun IdejoIdScreen(
    state: AddForumState,
    signIn: SignInState,
    onSignIn: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var another by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    val inbox = if (another) typed else IdejoIdViewModel.DEFAULT_INBOX
    val busy =
        state is AddForumState.Looking ||
            signIn is SignInState.Waiting ||
            signIn is SignInState.Exchanging
    Page(modifier) {
        ForumLogo(name = "idejo", size = 72.dp)
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.idejo_id_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.idejo_id_text),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Group(another, typed, { typed = it }, state, busy) { onSignIn(inbox) }
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(12.dp))
                Text(progressText(state, signIn))
            }
            Spacer(Modifier.height(16.dp))
        }
        Button(
            onClick = { onSignIn(inbox) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.idejo_id_sign_in)) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = onBack,
                enabled = !busy,
            ) { Text(stringResource(R.string.add_back)) }
            if (!another) {
                TextButton(onClick = { another = true }, enabled = !busy) {
                    Text(stringResource(R.string.idejo_id_another_group))
                }
            }
        }
    }
}

@Composable
private fun progressText(
    state: AddForumState,
    signIn: SignInState,
): String =
    when (signIn) {
        is SignInState.Waiting -> {
            stringResource(R.string.sign_in_waiting, signIn.forumName)
        }

        is SignInState.Exchanging -> {
            stringResource(R.string.sign_in_exchanging, signIn.forumName)
        }

        else -> {
            if (state is AddForumState.Looking) {
                stringResource(
                    R.string.idejo_id_looking,
                )
            } else {
                ""
            }
        }
    }

/** "Use another idejo group": the inbox's address; else only why idejo's own failed. */
@Composable
private fun Group(
    another: Boolean,
    typed: String,
    onType: (String) -> Unit,
    state: AddForumState,
    busy: Boolean,
    onGo: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val problem = (state as? AddForumState.Typing)?.problem
        if (another) {
            OutlinedTextField(
                value = typed,
                onValueChange = onType,
                label = { Text(stringResource(R.string.idejo_id_group_label)) },
                placeholder = { Text(stringResource(R.string.idejo_id_group_hint)) },
                singleLine = true,
                isError = problem != null,
                supportingText = problem?.let { { Text(problemText(it)) } },
                enabled = !busy,
                keyboardOptions =
                    KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onGo() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        } else if (problem != null) {
            Text(
                problemText(problem),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
