// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Android's network, for OF-05: lost, the open forum is offline at once; back, it is asked
 * again at once (`GET /server`), so the buttons that wait come back without the member
 * having to open a page.
 */
class NetworkWatch(
    private val sessions: SessionManager,
    private val available: Flow<Boolean>,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            available.distinctUntilChanged().collect { up -> sessions.active?.networkChanged(up) }
        }
    }

    companion object {
        /** Whether a default network is there, from the system's callbacks. */
        fun of(context: Context): Flow<Boolean> =
            callbackFlow {
                val manager = context.getSystemService(ConnectivityManager::class.java)
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            trySend(true)
                        }

                        override fun onLost(network: Network) {
                            trySend(false)
                        }
                    }
                manager.registerDefaultNetworkCallback(callback)
                awaitClose { manager.unregisterNetworkCallback(callback) }
            }
    }
}
