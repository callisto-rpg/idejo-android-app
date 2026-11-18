// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.notifications

import coil3.SingletonImageLoader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.idejo.app.core.data.AppScope
import org.idejo.app.core.data.ForegroundSockets
import org.idejo.app.core.data.PushRegistrar
import org.idejo.app.core.data.SessionManager
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import javax.inject.Inject

/**
 * The distributor's side of UnifiedPush (NT-02, NT-03): each forum's endpoint goes to the
 * registrar, each push, decrypted by the connector, to the notifier. `instance` is the
 * forum's origin.
 */
@AndroidEntryPoint
class IdejoPushService : PushService() {
    @Inject lateinit var registrar: PushRegistrar

    @Inject lateinit var sessions: SessionManager

    @Inject lateinit var foreground: ForegroundSockets

    @Inject @AppScope
    lateinit var scope: CoroutineScope

    private val notifier by lazy { PushNotifier(applicationContext) }

    override fun onNewEndpoint(
        endpoint: PushEndpoint,
        instance: String,
    ) {
        val keys = endpoint.pubKeySet ?: return
        scope.launch { registrar.onEndpoint(instance, endpoint.url, keys.pubKey, keys.auth) }
    }

    override fun onMessage(
        message: PushMessage,
        instance: String,
    ) {
        // A push the connector could not decrypt was not for these keys: nothing to show.
        if (!message.decrypted) return
        val session = sessions.active?.takeIf { it.origin == instance } ?: return
        val record = session.record.value
        val forum = PushForum(instance, record.name, logo(instance, record.discovery.logoUrl))
        notifier.show(forum, message.content, onScreen = foreground.inForeground)
    }

    override fun onRegistrationFailed(
        reason: FailedReason,
        instance: String,
    ) {
        registrar.onFailed(instance, reason.name)
    }

    override fun onUnregistered(instance: String) {
        notifier.forget(instance)
        scope.launch { registrar.onUnregistered(instance) }
    }

    /** The forum's logo from the image cache, never fetched here (NT-05). */
    private fun logo(
        origin: String,
        path: String?,
    ) = path
        ?.let { origin.toHttpUrlOrNull()?.resolve(it)?.toString() }
        ?.let { url ->
            SingletonImageLoader.get(applicationContext).diskCache?.openSnapshot(url)?.use {
                PushNotifier.logoAt(it.data.toString())
            }
        }
}
