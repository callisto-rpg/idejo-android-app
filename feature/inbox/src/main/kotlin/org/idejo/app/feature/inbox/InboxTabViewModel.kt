// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.idejo.app.core.data.InboxShortcut
import org.idejo.app.core.data.InboxShortcuts
import org.idejo.app.core.data.LastPlaces
import javax.inject.Inject

/**
 * The inbox's tab: where the logo menu sent the member (SH-13, SH-14), else the one left at
 * the last launch (MD-05); each tab shown is kept for the next.
 */
@HiltViewModel
class InboxTabViewModel
    @Inject
    constructor(
        shortcuts: InboxShortcuts,
        private val places: LastPlaces,
    ) : ViewModel() {
        /** Where the logo menu sent the member, taken once. */
        val opening: InboxShortcut? = shortcuts.take { it !is InboxShortcut.Connect }

        /** The tab shown first: **Forums**, unless the logo menu asked for another. */
        val firstTab: InboxTab
            get() =
                when (opening) {
                    InboxShortcut.Topics -> InboxTab.Topics
                    InboxShortcut.Chats -> InboxTab.Chats
                    else -> InboxTab.Forums
                }

        private val mutableRestored = MutableStateFlow<InboxTab?>(null)

        /** The tab left at the last launch, when the logo menu asked for none. */
        val restored: StateFlow<InboxTab?> = mutableRestored.asStateFlow()

        init {
            viewModelScope.launch {
                val kept = places.inboxTabAtLaunch() ?: return@launch
                if (opening == null) {
                    mutableRestored.value = InboxTab.entries.firstOrNull { it.name == kept }
                }
            }
        }

        fun shown(tab: InboxTab) {
            viewModelScope.launch { places.keepInboxTab(tab.name) }
        }
    }
