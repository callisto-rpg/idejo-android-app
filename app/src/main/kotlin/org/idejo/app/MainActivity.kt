// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.idejo.app.core.data.ForumRecord
import org.idejo.app.core.data.Launch
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.data.ThemeMode
import org.idejo.app.core.designsystem.theme.ForumTheme
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.feature.composer.SharedImages
import org.idejo.app.feature.onboarding.OnboardingFlow
import javax.inject.Inject

/** The one activity: the welcome screen, or the forum the app holds. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var sessions: SessionManager

    @Inject lateinit var sharedImages: SharedImages

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) receive(intent)
        enableEdgeToEdge()
        setContent {
            val launch by sessions.launch.collectAsStateWithLifecycle()
            when (val current = launch) {
                Launch.Loading -> {
                    IdejoTheme { Surface(Modifier.fillMaxSize()) {} }
                }

                Launch.Welcome -> {
                    IdejoTheme { OnboardingFlow() }
                }

                is Launch.Forum -> {
                    val record by current.session.record.collectAsStateWithLifecycle()
                    IdejoTheme(record.forumTheme(), record.dark()) {
                        key(current.session) { ForumApp(current.session) }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }
}

/** Pictures shared to the app (CO-14): read now, while the app may read them. */
private fun MainActivity.receive(intent: Intent?) {
    val uris = sharedUris(intent ?: return)
    if (uris.isNotEmpty()) lifecycleScope.launch { sharedImages.offer(contentResolver, uris) }
}

private fun sharedUris(intent: Intent): List<Uri> =
    when (intent.action) {
        Intent.ACTION_SEND -> {
            listOfNotNull(
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java),
            )
        }

        Intent.ACTION_SEND_MULTIPLE -> {
            IntentCompat
                .getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                .orEmpty()
        }

        else -> {
            emptyList()
        }
    }

/** The forum's colours, kept from its last `GET /server` (TH-06), or idejo's own. */
private fun ForumRecord.forumTheme(): ForumTheme =
    theme?.let {
        ForumTheme.fromHex(
            it.primary,
            it.secondary,
            it.tertiary,
            it.lightBackground,
            it.darkBackground,
        )
    }
        ?: ForumTheme.Default

@Composable
private fun ForumRecord.dark(): Boolean =
    when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
