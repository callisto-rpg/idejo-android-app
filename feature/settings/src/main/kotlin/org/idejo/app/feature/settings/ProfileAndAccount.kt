// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.idejo.app.core.data.AppMode
import org.idejo.app.core.data.GroupForums
import org.idejo.app.core.data.InboxShortcut
import org.idejo.app.core.data.InboxShortcuts
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Discovery
import org.idejo.app.feature.settings.appsdevices.AppsDevicesRoute
import org.idejo.app.feature.settings.deleteaccount.DeleteAccountRoute
import org.idejo.app.feature.settings.emailnotifications.EmailNotificationsRoute
import org.idejo.app.feature.settings.ignoredusers.IgnoredUsersRoute
import org.idejo.app.feature.settings.messagecontrols.MessageControlsRoute
import org.idejo.app.feature.settings.parts.SectionHeader
import org.idejo.app.feature.settings.password.PasswordRoute
import org.idejo.app.feature.settings.privatemessages.PrivateMessagesRoute
import org.idejo.app.feature.settings.profile.ProfileRoute
import org.idejo.app.feature.settings.pushnotifications.PushNotificationsRoute
import org.idejo.app.feature.settings.writing.WritingRoute
import javax.inject.Inject

/**
 * One section of **Profile and account** (ST): its key in the route, the web's tab it sits
 * under ([group]), its title, and its screen. Shown only when the forum's discovery lists
 * [feature], when it names one, and when [offered] holds for that discovery.
 */
@Immutable
class SettingsSection(
    val key: String,
    @param:StringRes val group: Int,
    @param:StringRes val title: Int,
    val feature: String? = null,
    val offered: (Discovery) -> Boolean = { true },
    val content: @Composable (Modifier) -> Unit,
)

/**
 * Every section, in the web's order (its tabs: Profile, Interface, Messages and
 * notifications, Account). **Adding a section is one entry here.**
 */
val SETTINGS_SECTIONS: List<SettingsSection> =
    listOf(
        SettingsSection("profile", R.string.group_profile, R.string.profile) { ProfileRoute(it) },
        SettingsSection("writing", R.string.group_interface, R.string.writing) { WritingRoute(it) },
        SettingsSection(
            "message-controls",
            R.string.group_interface,
            R.string.message_controls,
        ) { MessageControlsRoute(it) },
        SettingsSection(
            "private-messages",
            R.string.group_messages,
            R.string.private_messages,
            feature = "private_messages",
        ) { PrivateMessagesRoute(it) },
        SettingsSection(
            "ignored-users",
            R.string.group_messages,
            R.string.ignored_users,
        ) { IgnoredUsersRoute(it) },
        SettingsSection(
            "email-notifications",
            R.string.group_messages,
            R.string.email_notifications,
        ) { EmailNotificationsRoute(it) },
        SettingsSection(
            "push-notifications",
            R.string.group_messages,
            R.string.push_notifications,
            feature = "unifiedpush",
        ) { PushNotificationsRoute(it) },
        SettingsSection(
            "apps-devices",
            R.string.group_account,
            R.string.apps_devices,
        ) { AppsDevicesRoute(it) },
        SettingsSection(
            "change-password",
            R.string.group_account,
            R.string.password,
        ) { PasswordRoute(it) },
        SettingsSection(
            "delete-account",
            R.string.group_account,
            R.string.delete_account,
            offered = { it.account?.deletionUrl != null },
        ) { DeleteAccountRoute(it) },
    )

/** Whether a forum of [discovery] has this section; unknown, only the plain ones. */
internal fun SettingsSection.offeredBy(discovery: Discovery?): Boolean =
    if (discovery == null) {
        feature == null
    } else {
        (feature == null || feature in discovery.features) && offered(discovery)
    }

/** The overview: the sections this forum has, and the one open beside it on wide windows. */
@Immutable
data class ProfileAndAccountUiState(
    val sections: List<SettingsSection> = emptyList(),
    /** The section shown beside the list on wide windows (SH-60); null: the first. */
    val selected: String? = null,
    /**
     * A linked member's forum in idejo ID mode (ST-07), by name: these settings apply only
     * here, and the inbox's are the settings for all forums.
     */
    val onlyHere: String? = null,
)

/** What the member does on the overview. */
sealed interface ProfileAndAccountEvent {
    data class Open(
        val key: String,
    ) : ProfileAndAccountEvent

    /** **Settings for all forums** (ST-07): the inbox's Profile and account. */
    data object AllForums : ProfileAndAccountEvent
}

/**
 * The sections the open forum offers ([offeredBy]); for a linked member's forum in idejo ID
 * mode, its name for ST-07's note.
 */
@HiltViewModel
class ProfileAndAccountViewModel
    @Inject
    constructor(
        private val sessions: SessionManager,
        group: GroupForums,
        private val shortcuts: InboxShortcuts,
    ) : ViewModel() {
        private val session = sessions.active
        private val discovery = session?.record?.value?.discovery
        val sections: StateFlow<List<SettingsSection>> =
            MutableStateFlow(
                SETTINGS_SECTIONS.filter { it.offeredBy(discovery) },
            )

        /**
         * ST-07: the forum's name when the member came here with their idejo ID, the group's
         * inbox listing it among their forums (`GET /me/communities`); null on the inbox.
         */
        val onlyHere: StateFlow<String?> =
            combine(sessions.state, group.joined) { app, joined ->
                val origin = session?.origin
                session?.name?.takeIf {
                    app.mode == AppMode.IdejoId &&
                        app.inbox?.origin != origin &&
                        joined.any { it.url == origin }
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        /** **Settings for all forums** (ST-07): the inbox opens at its Profile and account. */
        fun allForums() {
            shortcuts.offer(InboxShortcut.Settings)
            viewModelScope.launch { sessions.openInbox() }
        }
    }

/**
 * **Profile and account** (ST), for the forum open. [section] null: the overview; a key: that
 * section. On wide windows (SH-60) the overview stays at the left and the section beside it.
 */
@Composable
fun ProfileAndAccountRoute(
    section: String?,
    onOpenSection: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileAndAccountViewModel = hiltViewModel(),
) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val onlyHere by viewModel.onlyHere.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf(section) }
    val wide = wideWindow()
    val state = ProfileAndAccountUiState(sections, selected, onlyHere)
    val shown = sections.firstOrNull { it.key == (selected ?: sections.firstOrNull()?.key) }
    when {
        wide -> {
            Row(modifier.fillMaxSize()) {
                ProfileAndAccountScreen(
                    state.copy(selected = shown?.key),
                    onEvent = { event ->
                        when (event) {
                            is ProfileAndAccountEvent.Open -> selected = event.key
                            ProfileAndAccountEvent.AllForums -> viewModel.allForums()
                        }
                    },
                    Modifier.width(LIST_WIDTH).fillMaxHeight(),
                )
                VerticalDivider()
                shown?.content?.invoke(Modifier.weight(1f))
            }
        }

        section == null -> {
            ProfileAndAccountScreen(
                state,
                onEvent = { event ->
                    when (event) {
                        is ProfileAndAccountEvent.Open -> onOpenSection(event.key)
                        ProfileAndAccountEvent.AllForums -> viewModel.allForums()
                    }
                },
                modifier,
            )
        }

        else -> {
            sections.firstOrNull { it.key == section }?.content?.invoke(modifier)
        }
    }
}

@Composable
fun ProfileAndAccountScreen(
    state: ProfileAndAccountUiState,
    onEvent: (ProfileAndAccountEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        state.onlyHere?.let { OnlyHere(it) { onEvent(ProfileAndAccountEvent.AllForums) } }
        var group: Int? = null
        state.sections.forEach { section ->
            if (section.group != group) {
                group = section.group
                SectionHeader(stringResource(section.group))
            }
            SectionLink(
                stringResource(section.title),
                selected = section.key == state.selected,
                onClick = { onEvent(ProfileAndAccountEvent.Open(section.key)) },
            )
        }
    }
}

/** ST-07, as the web's note: these settings are this forum's; the inbox's are everyone's. */
@Composable
private fun OnlyHere(
    forum: String,
    onAllForums: () -> Unit,
) {
    Column(
        Modifier.padding(top = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.settings_only_here, forum),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onAllForums) {
            Text(stringResource(R.string.settings_all_forums))
        }
    }
}

/** A section's line on the overview: a 56 dp target, highlighted when shown beside it. */
@Composable
private fun SectionLink(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colours = MaterialTheme.colorScheme
    Surface(
        color = if (selected) colours.secondaryContainer else colours.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .selectable(selected, role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = IdejoTheme.typography.bodyLarge)
        }
    }
}

/** The expanded width class, 840 dp and more (SH-60), as the shell docks its sidebar. */
@Composable
private fun wideWindow(): Boolean {
    val width = LocalWindowInfo.current.containerSize.width
    return with(LocalDensity.current) { width.toDp() } >= EXPANDED
}

private val EXPANDED = 840.dp
private val LIST_WIDTH = 320.dp

@Preview
@Composable
private fun ProfileAndAccountPreview() {
    IdejoTheme {
        ProfileAndAccountScreen(
            ProfileAndAccountUiState(SETTINGS_SECTIONS),
            onEvent = {},
        )
    }
}
