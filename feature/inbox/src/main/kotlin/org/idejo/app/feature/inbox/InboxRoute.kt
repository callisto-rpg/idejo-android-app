// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableSharedFlow
import org.idejo.app.core.data.InboxShortcut

/**
 * idejo ID mode's inbox (MD-02): **Forums**, **Topics** and **Chats** (IB-01 to IB-03,
 * IB-06: a chat opens with [onConversation]), and the group's directory (SI-08).
 */
@Composable
fun InboxRoute(
    onConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InboxViewModel = hiltViewModel(),
    tabs: InboxTabViewModel = hiltViewModel(),
) {
    var finding by rememberSaveable {
        mutableStateOf(tabs.opening == InboxShortcut.FindForums)
    }
    // The inbox's forum menu, while the inbox is shown (SH-13, SH-14).
    val asked = remember(tabs) { MutableSharedFlow<InboxTab>(extraBufferCapacity = 1) }
    LaunchedEffect(tabs) {
        tabs.asked.collect {
            finding = it == InboxShortcut.FindForums
            when (it) {
                InboxShortcut.Topics -> asked.tryEmit(InboxTab.Topics)
                InboxShortcut.Chats -> asked.tryEmit(InboxTab.Chats)
                is InboxShortcut.Connect -> asked.tryEmit(InboxTab.Forums)
                else -> Unit
            }
        }
    }
    if (finding) {
        BackHandler { finding = false }
        FindForumsRoute(onBack = { finding = false }, modifier = modifier)
        return
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val restored by tabs.restored.collectAsStateWithLifecycle()
    InboxScreen(
        state,
        forums = { ConnectForumsRoute(onFindMore = { finding = true }) },
        topics = { TopicsTab(state, viewModel::onEvent) },
        chats = { ConversationsRoute(onConversation, titled = false) },
        modifier = modifier,
        initialTab = tabs.firstTab,
        restoredTab = restored,
        onTab = tabs::shown,
        askedTab = asked,
    )
}
