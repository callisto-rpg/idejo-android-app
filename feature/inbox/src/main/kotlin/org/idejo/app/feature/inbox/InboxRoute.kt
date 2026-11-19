// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
) {
    var finding by rememberSaveable {
        mutableStateOf(viewModel.opening == InboxShortcut.FindForums)
    }
    if (finding) {
        BackHandler { finding = false }
        FindForumsRoute(onBack = { finding = false }, modifier = modifier)
        return
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    InboxScreen(
        state,
        forums = { ConnectForumsRoute(onFindMore = { finding = true }) },
        topics = { TopicsTab(state, viewModel::onEvent) },
        chats = { ConversationsRoute(onConversation, titled = false) },
        modifier = modifier,
        initialTab = viewModel.firstTab,
    )
}
