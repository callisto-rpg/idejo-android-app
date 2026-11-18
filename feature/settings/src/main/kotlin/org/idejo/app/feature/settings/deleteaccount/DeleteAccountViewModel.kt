// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.deleteaccount

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.idejo.app.core.data.SessionManager
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject

/**
 * **Delete account** (ST-06) on the forum open: the forum's `account.deletion_url` from its
 * discovery. Deleting stays on the web (API guide §10): members confirm there with their
 * password or sign-in provider, which needs a browser.
 */
@HiltViewModel
class DeleteAccountViewModel
    @Inject
    constructor(
        sessions: SessionManager,
    ) : ViewModel() {
        private val record = sessions.active?.record?.value
        val state: StateFlow<DeleteAccountUiState> =
            MutableStateFlow(
                DeleteAccountUiState(
                    deletionUrl =
                        record?.discovery?.account?.deletionUrl?.let {
                            deletionPage(record.origin, it)
                        },
                ),
            ).asStateFlow()
    }

/** [url] as an absolute address, a path being the forum's ([origin]); unreadable, null. */
internal fun deletionPage(
    origin: String,
    url: String,
): String? =
    try {
        URI(origin.trimEnd('/') + "/").resolve(url.trim()).takeIf { it.isAbsolute }?.toString()
    } catch (_: URISyntaxException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
