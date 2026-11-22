// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.idejo.app.core.data.AppMode
import org.idejo.app.core.data.CACHE_LIMITS_MB
import org.idejo.app.core.data.MessageText
import org.idejo.app.feature.settings.parts.RadioGroupRow
import org.idejo.app.feature.settings.parts.SwitchRow

/**
 * App settings above About (ST-20 to ST-22): **Mode** (MD-04), **Notifications** (the push
 * distributor), **Links** (open forum links in the app, DL-01), **Display** (text size
 * inside messages, haptics, reduced motion), **Storage** (the cache).
 */
internal fun LazyListScope.deviceSections(
    state: AppSettingsUiState,
    names: Map<String, String>,
    onEvent: (AppSettingsEvent) -> Unit,
    onIdejoId: () -> Unit = {},
    onEnablePush: () -> Unit = {},
) {
    state.mode?.let { mode ->
        item { SectionTitle(stringResource(R.string.app_mode)) }
        item { ModeChoice(mode, state, onEvent, onIdejoId) }
    }
    item { SectionTitle(stringResource(R.string.app_notifications)) }
    item { Push(state, names, onEvent, onEnablePush) }
    item { SectionTitle(stringResource(R.string.app_links)) }
    item { LinkHandlingSection() }
    item { SectionTitle(stringResource(R.string.app_display)) }
    item { Display(state, onEvent) }
    item { SectionTitle(stringResource(R.string.app_storage)) }
    item { Storage(state, onEvent) }
}

/**
 * Push on this device (NT-06, ST-20): on, through which distributor, with the others to
 * choose from and **Turn off**; or off, with **Enable push notifications**.
 */
@Composable
private fun Push(
    state: AppSettingsUiState,
    names: Map<String, String>,
    onEvent: (AppSettingsEvent) -> Unit,
    onEnable: () -> Unit,
) {
    Column {
        val distributor = state.distributor
        if (!state.pushOn || distributor == null) {
            Note(stringResource(R.string.app_push_off))
            Button(onClick = onEnable, Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.app_push_enable))
            }
            return@Column
        }
        Text(
            stringResource(R.string.app_push_on, names[distributor] ?: distributor),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (state.distributors.size > 1) {
            Note(stringResource(R.string.app_push_service))
            RadioGroupRow(
                options = state.distributors.map { it to (names[it] ?: it) },
                selected = distributor,
                onSelect = { onEvent(AppSettingsEvent.UseDistributor(it)) },
            )
        }
        if (state.mode == AppMode.IdejoId) {
            SwitchRow(
                label = stringResource(R.string.app_push_private),
                checked = state.privatePush,
                onCheckedChange = { onEvent(AppSettingsEvent.SetPrivatePush(it)) },
                helper =
                    stringResource(
                        R.string.app_push_private_helper,
                        state.inboxName ?: stringResource(R.string.app_push_inbox),
                    ),
            )
        }
        OutlinedButton(
            onClick = { onEvent(AppSettingsEvent.DisablePush) },
            Modifier.padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.app_push_turn_off))
        }
    }
}

@Composable
private fun Display(
    state: AppSettingsUiState,
    onEvent: (AppSettingsEvent) -> Unit,
) {
    Column {
        Note(stringResource(R.string.app_message_text))
        RadioGroupRow(
            options =
                listOf(
                    MessageText.Small to stringResource(R.string.app_text_small),
                    MessageText.Default to stringResource(R.string.app_text_default),
                    MessageText.Large to stringResource(R.string.app_text_large),
                    MessageText.Larger to stringResource(R.string.app_text_larger),
                ),
            selected = state.messageText,
            onSelect = { onEvent(AppSettingsEvent.SetMessageText(it)) },
        )
        SwitchRow(
            label = stringResource(R.string.app_haptics),
            checked = state.haptics,
            onCheckedChange = { onEvent(AppSettingsEvent.SetHaptics(it)) },
            helper = stringResource(R.string.app_haptics_helper),
        )
        SwitchRow(
            label = stringResource(R.string.app_reduce_motion),
            checked = state.reduceMotion,
            onCheckedChange = { onEvent(AppSettingsEvent.SetReduceMotion(it)) },
            helper = stringResource(R.string.app_reduce_motion_helper),
        )
    }
}

@Composable
private fun Storage(
    state: AppSettingsUiState,
    onEvent: (AppSettingsEvent) -> Unit,
) {
    Column {
        val context = LocalContext.current
        state.cacheBytes?.let { bytes ->
            Text(
                stringResource(
                    R.string.app_cache_used,
                    Formatter.formatShortFileSize(context, bytes),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Note(stringResource(R.string.app_cache_limit))
        RadioGroupRow(
            options = CACHE_LIMITS_MB.map { it to Formatter.formatShortFileSize(context, it * MB) },
            selected = state.cacheLimitMb,
            onSelect = { onEvent(AppSettingsEvent.SetCacheLimit(it)) },
        )
        Note(stringResource(R.string.app_cache_limit_helper))
        OutlinedButton(
            onClick = { onEvent(AppSettingsEvent.ClearCache) },
            Modifier.padding(top = 8.dp),
            enabled = !state.clearing,
        ) {
            Text(stringResource(R.string.app_clear_cache))
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.padding(top = 24.dp, bottom = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
internal fun Note(text: String) {
    Text(
        text,
        Modifier.padding(vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The distributors' names as Android shows them; the app's own is its FCM (NT-06). */
internal fun Context.distributorNames(packages: List<String>): Map<String, String> =
    packages.associateWith { pkg ->
        if (pkg == packageName) {
            getString(R.string.app_distributor_fcm)
        } else {
            try {
                packageManager
                    .getApplicationLabel(
                        packageManager.getApplicationInfo(pkg, 0),
                    ).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                pkg
            }
        }
    }

/** Bytes in a megabyte, as Android's file sizes count them (ST-21). */
private const val MB = 1000L * 1000
