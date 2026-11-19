// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl

/**
 * Forums' sign-in pages asked for by a ViewModel (SI-06, SI-08): [show] waits until the
 * screen says whether a Custom Tab opened ([opened]).
 */
class SignInTabs {
    private val mutable = MutableStateFlow<HttpUrl?>(null)
    val tab: StateFlow<HttpUrl?> = mutable.asStateFlow()
    private var waiting: CompletableDeferred<Boolean>? = null

    suspend fun show(url: HttpUrl): Boolean {
        val shown = CompletableDeferred<Boolean>()
        waiting = shown
        mutable.value = url
        return shown.await()
    }

    fun opened(shown: Boolean) {
        mutable.value = null
        waiting?.complete(shown)
    }
}

/**
 * Opens [tabs]' sign-in pages; back from one while [busy] without its answer, the member
 * closed it ([onClose]): the next forum's turn.
 */
@Composable
fun SignInTabsEffect(
    tabs: SignInTabs,
    busy: Boolean,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val close by rememberUpdatedState(onClose)
    val tab by tabs.tab.collectAsStateWithLifecycle()
    var leftForTab by remember { mutableStateOf(false) }
    LaunchedEffect(tab) {
        val url = tab ?: return@LaunchedEffect
        tabs.opened(context.openTab(url))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { leftForTab = busy }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (leftForTab) close()
        leftForTab = false
    }
}

/** A forum's sign-in page in a Custom Tab (SI-03); false without a browser. */
private fun Context.openTab(url: HttpUrl): Boolean =
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
