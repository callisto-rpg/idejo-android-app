// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.idejo.app.core.data.AppScope
import org.idejo.app.core.data.SignInCoordinator
import javax.inject.Inject

/**
 * Catches `org.idejo.app:/oauth/callback` (SI-03): hands the redirect to the sign-in in
 * progress, which checks its `state`, and brings the app back. Shows nothing.
 */
@AndroidEntryPoint
class OAuthCallbackActivity : ComponentActivity() {
    @Inject lateinit var signIn: SignInCoordinator

    @Inject @AppScope
    lateinit var scope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.toString()?.let { redirect -> scope.launch { signIn.complete(redirect) } }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
