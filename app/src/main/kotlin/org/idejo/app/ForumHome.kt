// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.idejo.app.core.data.Access
import org.idejo.app.core.data.ForumSession
import org.idejo.app.feature.onboarding.AccessNotice
import org.idejo.app.feature.onboarding.AccessViewModel
import org.idejo.app.feature.onboarding.rememberSignInAgain

/**
 * The forum, until the shell comes (next M1 pull request): its name, SI-09 and SI-10
 * notices, **Sign in** for a visitor (SI-05) and **Sign out** (SI-11).
 */
@Composable
fun ForumHome(
    session: ForumSession,
    modifier: Modifier = Modifier,
    access: AccessViewModel = hiltViewModel(),
) {
    val state by session.access.collectAsStateWithLifecycle()
    val signIn = rememberSignInAgain(access)
    LaunchedEffect(session) { session.refreshServer() }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(session.name, style = MaterialTheme.typography.headlineMedium)
            AccessNotice(state, session.name, onSignInAgain = signIn)
            when (state) {
                Access.Visitor -> {
                    OutlinedButton(onClick = signIn) { Text(stringResource(R.string.sign_in)) }
                }

                Access.SignedIn, is Access.Barred -> {
                    OutlinedButton(
                        onClick = access::signOut,
                    ) { Text(stringResource(R.string.sign_out)) }
                }

                Access.SignedOut -> {
                    Unit
                }
            }
            TextButton(onClick = access::remove) { Text(stringResource(R.string.remove_forum)) }
        }
    }
}
