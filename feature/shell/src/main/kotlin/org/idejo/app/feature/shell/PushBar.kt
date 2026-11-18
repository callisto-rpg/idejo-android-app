// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import org.idejo.app.core.designsystem.theme.IdejoTheme

/**
 * The web's notifications banner (NT-07, idejo PN-20 to PN-23), on category and topic pages,
 * in the page's flow under the top bar: on Android 13 and later, while the app may not
 * notify, once the member follows a topic (writing in one follows it) and push is on.
 * **Enable** asks Android; **Dismiss** keeps it away for 14 days (on this device).
 */
@Composable
internal fun PushBar(
    offered: Boolean,
    bubble: Boolean,
    place: Place,
    onDismiss: () -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (place !is Place.Category && place !is Place.Topic) return
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(context.mayNotify()) }
    var turnedOn by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        allowed = context.mayNotify()
        onPauseOrDispose {}
    }
    val ask =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            allowed = granted
            turnedOn = granted
            if (!granted) {
                // Android no longer shows its question once refused twice: its settings do.
                if (!context.mayAskAgain()) context.openNotificationSettings()
                onDismiss()
            }
        }
    if (turnedOn) {
        LaunchedEffect(Unit) {
            delay(ON_MILLIS)
            turnedOn = false
        }
        PushBarContent(on = true, bubble = false, onEnable = {}, onDismiss = {})
        return
    }
    if (allowed || !offered) return
    PushBarContent(
        on = false,
        bubble = bubble,
        onEnable = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) },
        onDismiss = onDismiss,
    )
}

/** The bar itself: the offer, or "Notifications are on" once they are. */
@Composable
internal fun PushBarContent(
    on: Boolean,
    bubble: Boolean,
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val region = stringResource(R.string.push_bar_region)
    Surface(
        modifier
            .fillMaxWidth()
            .semantics {
                paneTitle = region
                liveRegion = LiveRegionMode.Polite
            },
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_notifications), null, Modifier.size(20.dp))
                Text(
                    stringResource(if (on) R.string.push_bar_on else R.string.push_bar_offer),
                    style = IdejoTheme.typography.bodyMedium,
                )
            }
            if (!on) Actions(bubble, onEnable, onDismiss)
        }
    }
}

/** **Enable**, with its tutorial bubble under it (SH-21), and **Dismiss**, at the end. */
@Composable
private fun Actions(
    bubble: Boolean,
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.Top,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = onEnable) { Text(stringResource(R.string.push_bar_enable)) }
            if (bubble) {
                Text(
                    stringResource(R.string.push_bar_bubble),
                    Modifier
                        .padding(top = 2.dp)
                        .testTag(PUSH_BAR_BUBBLE)
                        // Not read by TalkBack, as the web's (aria-hidden).
                        .clearAndSetSemantics {},
                    style = IdejoTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.push_bar_dismiss)) }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Context.mayNotify(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Context.mayAskAgain(): Boolean {
    val activity = findActivity() ?: return true
    return ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.POST_NOTIFICATIONS,
    )
}

private fun Context.findActivity(): android.app.Activity? {
    var context: Context? = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}

private fun Context.openNotificationSettings() {
    startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private const val ON_MILLIS = 4_000L

/** The tutorial bubble under **Enable**, for tests (TalkBack does not read it). */
internal const val PUSH_BAR_BUBBLE = "push-bar-bubble"
