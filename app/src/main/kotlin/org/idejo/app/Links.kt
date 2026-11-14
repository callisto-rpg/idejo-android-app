// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * A link written in a forum's Markdown (About, Rules), in a Custom Tab, https only (SY-01):
 * a path is the forum's ([origin]). Nothing of the app's goes with it: no token, no cookie.
 * Opening the forum's own links in the app comes with deep links (DL).
 */
internal fun Context.openLink(
    origin: String,
    url: String,
) {
    val absolute =
        if (url.startsWith("/") &&
            !url.startsWith("//")
        ) {
            origin.trimEnd('/') + url
        } else {
            url
        }
    val uri = absolute.toUri()
    if (uri.scheme != "https") return
    try {
        CustomTabsIntent
            .Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(this, uri)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.no_browser, Toast.LENGTH_LONG).show()
    }
}
