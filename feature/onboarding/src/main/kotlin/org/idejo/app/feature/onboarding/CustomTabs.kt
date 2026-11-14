// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import okhttp3.HttpUrl

/** The device's model name, sent as `device_name` (SI-03): what **Apps and devices** shows. */
val deviceName: String get() = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"

/** Opens the forum's sign-in page in a Custom Tab (SI-03). False when there is no browser. */
fun Context.openSignIn(url: HttpUrl): Boolean =
    try {
        CustomTabsIntent
            .Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(this, url.toString().toUri())
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
