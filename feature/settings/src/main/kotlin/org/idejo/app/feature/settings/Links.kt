// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/**
 * A web page in a Custom Tab (the forum's **Delete account** page, ST-06; the source code,
 * ST-23), https only (SY-01). Nothing of the app's goes with it: no token, no cookie.
 */
internal fun Context.openPage(url: String) {
    val uri = url.toUri()
    if (uri.scheme != "https") return
    try {
        CustomTabsIntent
            .Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(this, uri)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.settings_no_browser, Toast.LENGTH_LONG).show()
    }
}
