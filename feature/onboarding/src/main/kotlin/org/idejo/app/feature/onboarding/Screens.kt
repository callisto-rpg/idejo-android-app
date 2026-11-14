// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import coil3.compose.AsyncImage
import org.idejo.app.core.data.SignInState
import org.idejo.app.core.designsystem.component.ForumLogo

/** The first launch (MD-01): welcome, then the address. Single-forum mode in M1. */
@Composable
fun OnboardingFlow(modifier: Modifier = Modifier) {
    var adding by rememberSaveable { mutableStateOf(false) }
    if (adding) {
        BackHandler { adding = false }
        AddForumRoute(onBack = { adding = false }, modifier = modifier)
    } else {
        WelcomeScreen(onEnterAddress = { adding = true }, modifier = modifier)
    }
}

/**
 * MD-01, in idejo's default theme. **Sign in with idejo ID** comes with idejo ID mode (M4;
 * steward decision 2026-11-14: the later milestone wins).
 */
@Composable
fun WelcomeScreen(
    onEnterAddress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Page(modifier) {
        ForumLogo(name = "idejo", size = 72.dp)
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.welcome_text),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        Button(onClick = onEnterAddress, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.welcome_enter_address))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.welcome_enter_address_line),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AddForumRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddForumViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signIn by viewModel.signInState.collectAsStateWithLifecycle()
    var noBrowser by remember { mutableStateOf(false) }
    var leftForTab by remember { mutableStateOf(false) }

    // Back from the Custom Tab without an answer: the member closed it (SI-03).
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { leftForTab = signIn is SignInState.Waiting }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (leftForTab) viewModel.tabClosed()
        leftForTab = false
    }

    AddForumScreen(
        state = state,
        signIn = signIn,
        onLookUp = viewModel::lookUp,
        onEdit = viewModel::edit,
        onSignIn = {
            viewModel.signIn()?.let { url ->
                if (!context.openSignIn(url)) {
                    viewModel.tabClosed()
                    noBrowser = true
                }
            }
        },
        onVisit = viewModel::visit,
        onBack = onBack,
        modifier = modifier,
    )
    SignInFailure(signIn, noBrowser) {
        noBrowser = false
        viewModel.dismissFailure()
    }
}

/** SI-01, SI-02: one field, then the forum's name and logo with Sign in and Read as a visitor. */
@Composable
fun AddForumScreen(
    state: AddForumState,
    signIn: SignInState,
    onLookUp: (String) -> Unit,
    onEdit: () -> Unit,
    onSignIn: () -> Unit,
    onVisit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    Page(modifier) {
        Text(stringResource(R.string.add_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        when (state) {
            is AddForumState.Found -> {
                FoundForum(state, signIn, onSignIn, onVisit, onEdit)
            }

            else -> {
                val problem = (state as? AddForumState.Typing)?.problem
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.add_address_label)) },
                    placeholder = { Text(stringResource(R.string.add_address_hint)) },
                    singleLine = true,
                    isError = problem != null,
                    supportingText = problem?.let { { Text(problemText(it)) } },
                    enabled = state !is AddForumState.Looking,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                        ),
                    keyboardActions = KeyboardActions(onGo = { onLookUp(typed) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                if (state is AddForumState.Looking) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.add_looking))
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onBack) { Text(stringResource(R.string.add_back)) }
                        Button(
                            onClick = { onLookUp(typed) },
                        ) { Text(stringResource(R.string.add_continue)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FoundForum(
    state: AddForumState.Found,
    signIn: SignInState,
    onSignIn: () -> Unit,
    onVisit: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val discovery = state.result.discovery
        val logo = discovery.logoUrl?.let { state.result.address.resolve(it) }?.toString()
        ForumLogo(
            name = discovery.name,
            size = 64.dp,
            image = logo?.let { { AsyncImage(model = it, contentDescription = null) } },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            discovery.name,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            state.result.address.origin.host,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        val busy = signIn is SignInState.Waiting || signIn is SignInState.Exchanging
        if (busy) SignInProgress(signIn)
        Button(onClick = onSignIn, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.add_sign_in))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onVisit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.add_visitor))
        }
        Text(
            stringResource(R.string.add_visitor_line),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onEdit, enabled = !busy) { Text(stringResource(R.string.add_other)) }
    }
}

@Composable
private fun SignInProgress(signIn: SignInState) {
    Row(Modifier.padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.size(12.dp))
        Text(
            when (signIn) {
                is SignInState.Waiting -> {
                    stringResource(R.string.sign_in_waiting, signIn.forumName)
                }

                is SignInState.Exchanging -> {
                    stringResource(
                        R.string.sign_in_exchanging,
                        signIn.forumName,
                    )
                }

                else -> {
                    ""
                }
            },
        )
    }
}

@Composable
private fun problemText(problem: AddForumState.Problem): String =
    when (problem) {
        is AddForumState.Problem.Address -> {
            when (problem.problem) {
                org.idejo.app.core.api.ForumAddress.Problem.Empty -> {
                    stringResource(
                        R.string.error_empty,
                    )
                }

                org.idejo.app.core.api.ForumAddress.Problem.NotHttps -> {
                    stringResource(
                        R.string.error_not_https,
                    )
                }

                org.idejo.app.core.api.ForumAddress.Problem.Malformed -> {
                    stringResource(
                        R.string.error_malformed,
                    )
                }
            }
        }

        AddForumState.Problem.NotIdejo -> {
            stringResource(R.string.error_not_idejo)
        }

        is AddForumState.Problem.AppTurnedOff -> {
            stringResource(R.string.error_app_off, problem.name)
        }

        AddForumState.Problem.NeedsNewerApp -> {
            stringResource(R.string.error_newer_app)
        }

        AddForumState.Problem.Unreachable -> {
            stringResource(R.string.error_unreachable)
        }
    }

/** A sign-in that did not finish, or no browser to sign in with. */
@Composable
fun SignInFailure(
    state: SignInState,
    noBrowser: Boolean,
    onDismiss: () -> Unit,
) {
    val text =
        when {
            noBrowser -> {
                stringResource(R.string.sign_in_no_browser)
            }

            state is SignInState.Failed -> {
                when (state.reason) {
                    SignInState.Reason.NoSignIn -> {
                        stringResource(
                            R.string.sign_in_no_sign_in,
                            state.forumName,
                        )
                    }

                    SignInState.Reason.Refused -> {
                        stringResource(
                            R.string.sign_in_refused,
                            state.forumName,
                        )
                    }

                    SignInState.Reason.Unreachable -> {
                        stringResource(
                            R.string.sign_in_unreachable,
                            state.forumName,
                        )
                    }

                    SignInState.Reason.Expired -> {
                        stringResource(R.string.sign_in_expired)
                    }
                }
            }

            else -> {
                return
            }
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onDismiss,
            ) { Text(stringResource(R.string.sign_in_ok)) }
        },
        text = { Text(text) },
    )
}

@Composable
private fun Page(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                Modifier.widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { content() }
        }
    }
}
