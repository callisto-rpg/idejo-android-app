// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/**
 * **Enable push notifications** (NT-06): Android 13's notification permission first, then
 * [enable] with the activity, whatever the answer (refused, push still registers; Android
 * shows nothing until it is allowed).
 */
@Composable
internal fun rememberEnablePush(enable: (Activity) -> Unit): () -> Unit {
    val context = LocalContext.current
    val ask =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            context.findActivity()?.let(enable)
        }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.findActivity()?.let(enable)
        }
    }
}

/** No distributor at all, nor Google Play services or microG: Sunup is offered (NT-06). */
@Composable
internal fun SunupDialog(onAnswer: (install: Boolean) -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        title = { Text(stringResource(R.string.app_sunup_title)) },
        text = { Text(stringResource(R.string.app_sunup_text)) },
        confirmButton = {
            TextButton(onClick = {
                context.installSunup()
                onAnswer(true)
            }) { Text(stringResource(R.string.app_sunup_install)) }
        },
        dismissButton = {
            TextButton(onClick = { onAnswer(false) }) {
                Text(stringResource(R.string.app_sunup_not_now))
            }
        },
    )
}

/** Sunup in the device's app store; without one, UnifiedPush's list of distributors. */
private fun Context.installSunup() {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, SUNUP_MARKET.toUri()))
    } catch (_: ActivityNotFoundException) {
        openPage(DISTRIBUTORS_URL)
    }
}

internal fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private const val SUNUP_MARKET = "market://details?id=org.unifiedpush.distributor.sunup"
private const val DISTRIBUTORS_URL = "https://unifiedpush.org/users/distributors/"
