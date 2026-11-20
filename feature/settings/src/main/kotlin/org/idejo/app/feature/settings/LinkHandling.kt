// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Whether forum links open in the app (ST-20, DL-01), as Android has it. */
sealed interface LinkHandling {
    /** Before Android 12: the system asks, or remembers the member's answer. */
    data object SystemAsks : LinkHandling

    /** The member turned the app's links off in Android's settings. */
    data object Off : LinkHandling

    /** [open] of the [total] addresses the app declares open in it. */
    data class On(
        val open: Int,
        val total: Int,
    ) : LinkHandling
}

/**
 * From Android's per-host states: verified by the forum (IX-03) or chosen by the member
 * count as open; `*.idejo.org` counts as one address.
 */
fun linkHandling(
    allowed: Boolean,
    states: Map<String, Int>,
): LinkHandling =
    if (!allowed) {
        LinkHandling.Off
    } else {
        LinkHandling.On(open = states.values.count { it != NONE }, total = states.size)
    }

/** `DomainVerificationUserState.DOMAIN_STATE_NONE`. */
private const val NONE = 0

private fun Context.linkHandling(): LinkHandling {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return LinkHandling.SystemAsks
    val manager = getSystemService(DomainVerificationManager::class.java)
    val state: DomainVerificationUserState =
        manager?.getDomainVerificationUserState(packageName) ?: return LinkHandling.SystemAsks
    return linkHandling(state.isLinkHandlingAllowed, state.hostToStateMap)
}

/** Android's page for the app's links (Open by default), else the app's details. */
private fun Context.openLinkSettings() {
    val app = "package:$packageName".toUri()
    val intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, app)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)
        }
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * **Open links in the app** (ST-20): Android keeps this choice, so the section says what it
 * is, read again each time the member comes back, and leads to Android's page to change it.
 */
@Composable
internal fun LinkHandlingSection() {
    val context = LocalContext.current
    var handling by remember { mutableStateOf(context.linkHandling()) }
    LifecycleResumeEffect(Unit) {
        handling = context.linkHandling()
        onPauseOrDispose {}
    }
    LinkHandlingRow(handling, onChange = { context.openLinkSettings() })
}

@Composable
internal fun LinkHandlingRow(
    handling: LinkHandling,
    onChange: () -> Unit,
) {
    Column {
        Note(
            when (handling) {
                LinkHandling.SystemAsks -> {
                    stringResource(R.string.app_links_system)
                }

                LinkHandling.Off -> {
                    stringResource(R.string.app_links_off)
                }

                is LinkHandling.On -> {
                    if (handling.open == 0) {
                        stringResource(R.string.app_links_unverified)
                    } else {
                        pluralStringResource(
                            R.plurals.app_links_on,
                            handling.total,
                            handling.open,
                            handling.total,
                        )
                    }
                }
            },
        )
        OutlinedButton(onClick = onChange) { Text(stringResource(R.string.app_links_change)) }
    }
}
