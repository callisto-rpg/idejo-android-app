// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/** **Share link** (SH-11) and a topic's **Share** (SH-37): the system's share sheet. */
internal fun Context.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(send, null))
}

/** A topic's **Copy link** (SH-37). */
internal fun Context.copyLink(url: String) {
    getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(getString(R.string.topic_menu_copy_link), url))
    // Android 13 and later say so themselves.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, R.string.topic_menu_link_copied, Toast.LENGTH_SHORT).show()
    }
}

/**
 * A web page of the forum (Admin, SH-24; Sign up, SH-25) in a Custom Tab, https only (SY-01).
 * Nothing of the app's goes with it: no token, no cookie.
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
        Toast.makeText(this, R.string.no_browser, Toast.LENGTH_LONG).show()
    }
}
