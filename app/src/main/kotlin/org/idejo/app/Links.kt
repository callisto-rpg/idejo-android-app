// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/** [url] made absolute: a path is the forum's ([origin]). */
internal fun absoluteLink(
    origin: String,
    url: String,
): String = if (url.startsWith("/") && !url.startsWith("//")) origin.trimEnd('/') + url else url

/**
 * A link in a Custom Tab, https only (SY-01), its toolbar the forum's [toolbar] colour
 * (DL-03). Nothing of the app's goes with it: no token, no cookie. Links to the forums held
 * open in the app first (`rememberLinkOpener`).
 */
internal fun Context.openLink(
    origin: String,
    url: String,
    toolbar: Int? = null,
) {
    val uri = absoluteLink(origin, url).toUri()
    if (uri.scheme != "https") return
    val colours =
        CustomTabColorSchemeParams
            .Builder()
            .apply { toolbar?.let { setToolbarColor(it) } }
            .build()
    try {
        CustomTabsIntent
            .Builder()
            .setShowTitle(true)
            .setDefaultColorSchemeParams(colours)
            .build()
            .launchUrl(this, uri)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.no_browser, Toast.LENGTH_LONG).show()
    }
}
