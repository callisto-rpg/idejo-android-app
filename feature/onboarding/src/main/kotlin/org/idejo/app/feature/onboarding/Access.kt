// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import org.idejo.app.core.data.Access
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.data.SignInCoordinator
import org.idejo.app.core.data.SignInState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

/** Signing in again, out, or away, on the open forum (SI-05, SI-09, SI-11). */
@HiltViewModel
class AccessViewModel
    @Inject
    constructor(
        private val sessions: SessionManager,
        private val signIn: SignInCoordinator,
    ) : ViewModel() {
        val signInState = signIn.state

        /** The forum's sign-in page, or null (the failure is in [signInState]). */
        fun signInAgain(): HttpUrl? = sessions.active?.let { signIn.startAgain(it, deviceName) }

        fun tabClosed() = signIn.cancelIfWaiting()

        fun dismissFailure() = signIn.dismiss()

        /** SI-11: revoked, forgotten here; the forum stays, as a visitor. */
        fun signOut() {
            val origin = sessions.active?.origin ?: return
            viewModelScope.launch { sessions.signOut(origin) }
        }

        /** Signs out and removes the forum (single-forum mode: back to the welcome screen). */
        fun remove() {
            val origin = sessions.active?.origin ?: return
            viewModelScope.launch { sessions.remove(origin) }
        }
    }

/** A sign-in launcher for **Sign in** and **Sign in again**, with its failure dialog. */
@Composable
fun rememberSignInAgain(viewModel: AccessViewModel = hiltViewModel()): () -> Unit {
    val context = LocalContext.current
    val state by viewModel.signInState.collectAsStateWithLifecycle()
    var noBrowser by remember { mutableStateOf(false) }
    var leftForTab by remember { mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
        leftForTab = state is SignInState.Waiting
    }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        if (leftForTab) viewModel.tabClosed()
        leftForTab = false
    }
    SignInFailure(state, noBrowser) {
        noBrowser = false
        viewModel.dismissFailure()
    }
    return {
        viewModel.signInAgain()?.let { url ->
            if (!context.openSignIn(url)) {
                viewModel.tabClosed()
                noBrowser = true
            }
        }
    }
}

/**
 * SI-09 ("You were signed out of {name}", with **Sign in again**) and SI-10 (the server's
 * message, and until when). Nothing for a member signed in or a visitor.
 */
@Composable
fun AccessNotice(
    access: Access,
    forumName: String,
    onSignInAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (access) {
        Access.SignedOut -> {
            Notice(modifier) {
                Text(
                    stringResource(R.string.signed_out_title, forumName),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.signed_out_text),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onSignInAgain, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.sign_in_again))
                }
            }
        }

        is Access.Barred -> {
            Notice(modifier) {
                Text(access.message, style = MaterialTheme.typography.bodyLarge)
                access.until?.let {
                    val date =
                        DateTimeFormatter
                            .ofLocalizedDateTime(FormatStyle.MEDIUM)
                            .withZone(ZoneId.systemDefault())
                            .format(it)
                    Text(
                        stringResource(R.string.barred_until, date),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Access.SignedIn, Access.Visitor -> {
            Unit
        }
    }
}

@Composable
private fun Notice(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}
