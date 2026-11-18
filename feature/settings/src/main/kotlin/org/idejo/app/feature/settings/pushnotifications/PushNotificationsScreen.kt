// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.pushnotifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.NotificationSettings
import org.idejo.app.core.model.PushRegistration
import org.idejo.app.feature.settings.R
import org.idejo.app.feature.settings.parts.ActionRow
import org.idejo.app.feature.settings.parts.ErrorState
import org.idejo.app.feature.settings.parts.LoadingState
import org.idejo.app.feature.settings.parts.ProblemText
import org.idejo.app.feature.settings.parts.SectionHeader
import org.idejo.app.feature.settings.parts.SwitchRow
import org.idejo.app.feature.settings.parts.day
import java.time.Instant

/** One of the five push types (NT-08, idejo PN-11), in the web's order. */
enum class PushChoice {
    Reply,
    Mention,
    RoleMention,
    Followed,
    PrivateMessages,
}

/** What **Send test notification** came to. */
enum class TestResult {
    /** Queued for at least one device. */
    Sent,

    /** No device takes notifications yet. */
    NoDevice,
}

/**
 * **Push notifications** (NT-08): whether this device takes them, the five types for all the
 * member's devices, the devices with **Remove**, and **Send test notification**. A null field
 * is one the server does not send: its row is hidden.
 */
@Immutable
data class PushNotificationsUiState(
    /** True until the first answers. */
    val loading: Boolean = true,
    /** Why the section could not be read: the error state, with **Try again**. */
    val loadProblem: ApiProblem? = null,
    /** The push types, as the server last answered or as just changed. */
    val push: NotificationSettings.Push? = null,
    /** The browsers and devices taking push; null until the first answer. */
    val devices: List<PushRegistration>? = null,
    /** The member's username, for "@{username} mentions". */
    val username: String? = null,
    /** A switch's change is on its way: the switches wait. */
    val saving: Boolean = false,
    /** The id of the device being removed: the buttons wait. */
    val removing: String? = null,
    /** A test notification is on its way. */
    val testing: Boolean = false,
    val tested: TestResult? = null,
    /** Why the last change, removal or test was not done. */
    val problem: ApiProblem? = null,
) {
    /** One of the registrations is this device's (NT-02). */
    val thisDeviceOn: Boolean get() = devices.orEmpty().any { it.thisDevice }
}

/** What the member does on **Push notifications**. */
sealed interface PushNotificationsEvent {
    data class Set(
        val choice: PushChoice,
        val on: Boolean,
    ) : PushNotificationsEvent

    data class Remove(
        val id: String,
    ) : PushNotificationsEvent

    data object SendTest : PushNotificationsEvent

    data object Retry : PushNotificationsEvent
}

@Composable
fun PushNotificationsRoute(
    modifier: Modifier = Modifier,
    viewModel: PushNotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PushNotificationsScreen(state, viewModel::onEvent, modifier)
}

@Composable
fun PushNotificationsScreen(
    state: PushNotificationsUiState,
    onEvent: (PushNotificationsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionHeader(stringResource(R.string.push_notifications))
        when {
            state.loadProblem != null -> {
                ErrorState(state.loadProblem, onRetry = { onEvent(PushNotificationsEvent.Retry) })
            }

            state.loading -> {
                LoadingState()
            }

            else -> {
                Text(
                    stringResource(
                        if (state.thisDeviceOn) R.string.push_this_on else R.string.push_this_off,
                    ),
                    Modifier.padding(bottom = 12.dp),
                    style = IdejoTheme.typography.bodyLarge,
                )
                state.push?.let { push -> Choices(push, state, onEvent) }
                Devices(state, onEvent)
                Test(state, onEvent)
                state.problem?.let { ProblemText(it) }
            }
        }
    }
}

@Composable
private fun Choices(
    push: NotificationSettings.Push,
    state: PushNotificationsUiState,
    onEvent: (PushNotificationsEvent) -> Unit,
) {
    if (PushChoice.entries.none { push[it] != null }) return
    Column {
        Text(
            stringResource(R.string.push_send_for),
            Modifier.padding(bottom = 8.dp),
            style = IdejoTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PushChoice.entries.forEach { choice ->
            push[choice]?.let { on ->
                SwitchRow(
                    label = label(choice, state.username),
                    checked = on,
                    enabled = !state.saving,
                    onCheckedChange = { onEvent(PushNotificationsEvent.Set(choice, it)) },
                )
            }
        }
        Text(
            stringResource(R.string.push_all_devices),
            Modifier.padding(top = 4.dp),
            style = IdejoTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Devices(
    state: PushNotificationsUiState,
    onEvent: (PushNotificationsEvent) -> Unit,
) {
    Column {
        SectionHeader(stringResource(R.string.push_devices))
        val devices = state.devices.orEmpty()
        if (devices.isEmpty()) {
            Text(
                stringResource(R.string.push_no_devices),
                style = IdejoTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        devices.forEach { device ->
            val name =
                device.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.push_unnamed)
            ActionRow(
                title = name,
                detail =
                    listOfNotNull(
                        stringResource(R.string.push_this_device).takeIf { device.thisDevice },
                        device.createdAt?.let { stringResource(R.string.push_added, day(it)) },
                    ).joinToString(" · ").ifEmpty { null },
                action = stringResource(R.string.push_remove),
                actionLabel = stringResource(R.string.push_remove_named, name),
                enabled = state.removing == null,
                onAction = { onEvent(PushNotificationsEvent.Remove(device.id)) },
            )
        }
    }
}

@Composable
private fun Test(
    state: PushNotificationsUiState,
    onEvent: (PushNotificationsEvent) -> Unit,
) {
    Column {
        OutlinedButton(
            onClick = { onEvent(PushNotificationsEvent.SendTest) },
            Modifier.padding(top = 16.dp),
            enabled = !state.testing,
        ) {
            Text(stringResource(R.string.push_send_test))
        }
        state.tested?.let { result ->
            Text(
                stringResource(
                    when (result) {
                        TestResult.Sent -> R.string.push_test_sent
                        TestResult.NoDevice -> R.string.push_test_no_device
                    },
                ),
                Modifier
                    .padding(top = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = IdejoTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun label(
    choice: PushChoice,
    username: String?,
): String =
    when (choice) {
        PushChoice.Reply -> {
            stringResource(R.string.push_reply)
        }

        PushChoice.Mention -> {
            if (username.isNullOrBlank()) {
                stringResource(R.string.push_mention_plain)
            } else {
                stringResource(R.string.push_mention, username)
            }
        }

        PushChoice.RoleMention -> {
            stringResource(R.string.push_role_mention)
        }

        PushChoice.Followed -> {
            stringResource(R.string.push_followed)
        }

        PushChoice.PrivateMessages -> {
            stringResource(R.string.push_private)
        }
    }

/** The choice's value, null when the server does not send it. */
operator fun NotificationSettings.Push.get(choice: PushChoice): Boolean? =
    when (choice) {
        PushChoice.Reply -> reply
        PushChoice.Mention -> mention
        PushChoice.RoleMention -> roleMention
        PushChoice.Followed -> followed
        PushChoice.PrivateMessages -> privateMessages
    }

/** A copy with [choice] set to [on]. */
fun NotificationSettings.Push.with(
    choice: PushChoice,
    on: Boolean?,
): NotificationSettings.Push =
    when (choice) {
        PushChoice.Reply -> copy(reply = on)
        PushChoice.Mention -> copy(mention = on)
        PushChoice.RoleMention -> copy(roleMention = on)
        PushChoice.Followed -> copy(followed = on)
        PushChoice.PrivateMessages -> copy(privateMessages = on)
    }

@Preview
@Composable
private fun PushNotificationsScreenPreview() {
    IdejoTheme {
        PushNotificationsScreen(
            PushNotificationsUiState(
                loading = false,
                username = "ada",
                push = NotificationSettings.Push(true, true, true, false, true),
                devices =
                    listOf(
                        PushRegistration(
                            "p_1",
                            "unifiedpush",
                            "Pixel 8",
                            thisDevice = true,
                            createdAt = Instant.EPOCH,
                        ),
                        PushRegistration("p_2", "web", "Firefox on Linux"),
                    ),
            ),
            onEvent = {},
        )
    }
}
