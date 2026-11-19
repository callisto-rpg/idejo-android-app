// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.emailnotifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.NotificationSettings
import org.idejo.app.feature.settings.R
import org.idejo.app.feature.settings.parts.ErrorState
import org.idejo.app.feature.settings.parts.LoadingState
import org.idejo.app.feature.settings.parts.ProblemText
import org.idejo.app.feature.settings.parts.SaveForAllForums
import org.idejo.app.feature.settings.parts.SectionHeader
import org.idejo.app.feature.settings.parts.SwitchRow

/** One of the email summary's choices (ST-03, idejo NO-04), in the web's order. */
enum class EmailChoice {
    Mentions,
    RoleMentions,
    OwnTopics,
    FollowedTopics,
    NewTopics,
    AllActivity,
    OnceAWeek,
}

/**
 * **Email notifications** (ST-03): the summary sent after 24 hours away. A null choice is
 * one the server does not send: its row is hidden rather than showing a guess.
 */
@Immutable
data class EmailNotificationsUiState(
    /** True until the first `GET /me/notification-settings` answer. */
    val loading: Boolean = true,
    /** Why the section could not be read: the error state, with **Try again**. */
    val loadProblem: ApiProblem? = null,
    /** The choices, as the server last answered or as just changed. */
    val email: NotificationSettings.Email? = null,
    /** The member's username, for "@{username} mentions and replies to my posts". */
    val username: String? = null,
    /** A change is on its way to the server: the switches wait. */
    val saving: Boolean = false,
    /** Why the last change was not made; the switch is back as it was. */
    val problem: ApiProblem? = null,
)

/** What the member does on **Email notifications**. */
sealed interface EmailNotificationsEvent {
    /** One switch: [choice] turned [on]. */
    data class Set(
        val choice: EmailChoice,
        val on: Boolean,
    ) : EmailNotificationsEvent

    data object Retry : EmailNotificationsEvent
}

@Composable
fun EmailNotificationsRoute(
    modifier: Modifier = Modifier,
    viewModel: EmailNotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EmailNotificationsScreen(state, viewModel::onEvent, modifier) { SaveForAllForums(it) }
}

@Composable
fun EmailNotificationsScreen(
    state: EmailNotificationsUiState,
    onEvent: (EmailNotificationsEvent) -> Unit,
    modifier: Modifier = Modifier,
    everywhere: @Composable (sections: List<String>) -> Unit = {},
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionHeader(stringResource(R.string.email_notifications))
        when {
            state.loadProblem != null -> {
                ErrorState(state.loadProblem, onRetry = { onEvent(EmailNotificationsEvent.Retry) })
            }

            state.loading -> {
                LoadingState()
            }

            else -> {
                state.email?.let { email -> Choices(email, state, onEvent) }
                everywhere(listOf("email"))
                state.problem?.let { ProblemText(it) }
            }
        }
    }
}

@Composable
private fun Choices(
    email: NotificationSettings.Email,
    state: EmailNotificationsUiState,
    onEvent: (EmailNotificationsEvent) -> Unit,
) {
    val summary = EmailChoice.entries.filter { it != EmailChoice.OnceAWeek }
    if (summary.any { email[it] != null }) {
        Text(
            stringResource(R.string.email_notifications_when),
            Modifier.padding(bottom = 8.dp),
            style = IdejoTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    EmailChoice.entries.forEach { choice ->
        email[choice]?.let { on ->
            SwitchRow(
                label = label(choice, state.username),
                checked = on,
                enabled = !state.saving,
                onCheckedChange = { onEvent(EmailNotificationsEvent.Set(choice, it)) },
            )
        }
    }
}

@Composable
private fun label(
    choice: EmailChoice,
    username: String?,
): String =
    when (choice) {
        EmailChoice.Mentions -> {
            if (username.isNullOrBlank()) {
                stringResource(R.string.email_notifications_mentions_plain)
            } else {
                stringResource(R.string.email_notifications_mentions, username)
            }
        }

        EmailChoice.RoleMentions -> {
            stringResource(R.string.email_notifications_role_mentions)
        }

        EmailChoice.OwnTopics -> {
            stringResource(R.string.email_notifications_own_topics)
        }

        EmailChoice.FollowedTopics -> {
            stringResource(R.string.email_notifications_followed_topics)
        }

        EmailChoice.NewTopics -> {
            stringResource(R.string.email_notifications_new_topics)
        }

        EmailChoice.AllActivity -> {
            stringResource(R.string.email_notifications_all_activity)
        }

        EmailChoice.OnceAWeek -> {
            stringResource(R.string.email_notifications_once_a_week)
        }
    }

/** The choice's value, null when the server does not send it. */
operator fun NotificationSettings.Email.get(choice: EmailChoice): Boolean? =
    when (choice) {
        EmailChoice.Mentions -> mentions
        EmailChoice.RoleMentions -> roleMentions
        EmailChoice.OwnTopics -> ownTopics
        EmailChoice.FollowedTopics -> followedTopics
        EmailChoice.NewTopics -> newTopics
        EmailChoice.AllActivity -> allActivity
        EmailChoice.OnceAWeek -> onceAWeek
    }

/** A copy with [choice] set to [on]; null leaves it unsent (a `PATCH` of that field alone). */
fun NotificationSettings.Email.with(
    choice: EmailChoice,
    on: Boolean?,
): NotificationSettings.Email =
    when (choice) {
        EmailChoice.Mentions -> copy(mentions = on)
        EmailChoice.RoleMentions -> copy(roleMentions = on)
        EmailChoice.OwnTopics -> copy(ownTopics = on)
        EmailChoice.FollowedTopics -> copy(followedTopics = on)
        EmailChoice.NewTopics -> copy(newTopics = on)
        EmailChoice.AllActivity -> copy(allActivity = on)
        EmailChoice.OnceAWeek -> copy(onceAWeek = on)
    }

@Preview
@Composable
private fun EmailNotificationsScreenPreview() {
    IdejoTheme {
        EmailNotificationsScreen(
            EmailNotificationsUiState(
                loading = false,
                username = "ada",
                email =
                    NotificationSettings.Email(
                        mentions = true,
                        roleMentions = false,
                        ownTopics = true,
                        followedTopics = false,
                        newTopics = false,
                        allActivity = false,
                        onceAWeek = true,
                    ),
            ),
            onEvent = {},
        )
    }
}
