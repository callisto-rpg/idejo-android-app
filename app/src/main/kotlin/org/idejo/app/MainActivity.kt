// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import org.idejo.app.core.data.ForumRecord
import org.idejo.app.core.data.Launch
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.data.ThemeMode
import org.idejo.app.core.designsystem.theme.ForumTheme
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.feature.onboarding.OnboardingFlow
import javax.inject.Inject

/** The one activity: the welcome screen, or the forum the app holds. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var sessions: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    IdejoTheme(record.forumTheme(), record.dark()) { ForumHome(current.session) }
                }
            }
        }
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
