// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.data.GroupForums
import org.idejo.app.core.data.InboxChats
import org.idejo.app.core.data.InboxShortcut
import org.idejo.app.core.data.InboxShortcuts
import org.idejo.app.core.data.InboxTopics
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.designsystem.component.LocalTopBarMenu
import org.idejo.app.core.designsystem.component.TopBarMenu
import org.idejo.app.core.designsystem.theme.IdejoTheme
import javax.inject.Inject

/**
 * The group's inbox with the forum menu (SH-10, steward request 2026-11-23): a pull down on
 * any top bar of [content] brings [InboxMenu] under it with the finger, as in a forum
 * ([MenuPanel]), so the forum menu can be pulled down wherever the member is. Its shortcuts
 * go to the inbox's tabs ([onHome], which they read from `InboxShortcuts`); **By address** is
 * [onAddForum].
 */
@Composable
fun InboxMenuFrame(
    onHome: () -> Unit,
    onAddForum: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InboxMenuViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val menu by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val reduced by rememberUpdatedState(IdejoTheme.reducedMotion)
    val sliding = remember { Sliding(scope, { reduced }, { open = true }, { open = false }) }
    LaunchedEffect(open) { if (open) viewModel.opened() }
    BackHandler(enabled = open) { open = false }
    var frameTop by remember { mutableFloatStateOf(0f) }
    var barBottom by remember { mutableFloatStateOf(0f) }
    val menuOver =
        remember(sliding) {
            TopBarMenu(
                pull =
                    Modifier
                        .onGloballyPositioned { barBottom = it.boundsInRoot().bottom }
                        .pullDown { sliding },
                // The page under the bar blurs as the menu comes in, as a forum's.
                below = Modifier.glassBehind { sliding.fraction },
            )
        }
    val home by rememberUpdatedState(onHome)
    val addForum by rememberUpdatedState(onAddForum)
    val actions =
        remember(viewModel, context) {
            inboxMenuActions(viewModel, context, {
                open = false
                home()
            }) {
                open = false
                addForum()
            }
        }
    Box(modifier.fillMaxSize().onGloballyPositioned { frameTop = it.positionInRoot().y }) {
        CompositionLocalProvider(LocalTopBarMenu provides menuOver) { content() }
        val top = with(LocalDensity.current) { (barBottom - frameTop).coerceAtLeast(0f).toDp() }
        MenuPanel(sliding, open = open, top = top, tag = FORUM_MENU, onClose = { open = false }) {
            InboxMenu(menu, actions)
        }
    }
}

private fun inboxMenuActions(
    viewModel: InboxMenuViewModel,
    context: Context,
    home: () -> Unit,
    addForum: () -> Unit,
): LogoMenuActions =
    object : LogoMenuActions {
        override fun enter(forum: YourForum) = viewModel.enter(forum)

        override fun connect(forum: YourForum) {
            viewModel.ask(InboxShortcut.Connect(forum.origin))
            home()
        }

        override fun remove(forum: YourForum) = viewModel.remove(forum)

        override fun openInBrowser(forum: YourForum) = context.openPage(forum.origin)

        override fun forums() {
            viewModel.ask(InboxShortcut.Forums)
            home()
        }

        override fun topics() {
            viewModel.ask(InboxShortcut.Topics)
            home()
        }

        override fun chats() {
            viewModel.ask(InboxShortcut.Chats)
            home()
        }

        override fun findForums() {
            viewModel.ask(InboxShortcut.FindForums)
            home()
        }

        override fun addByAddress() = addForum()

        // Already in idejo ID mode: the inbox is its group's.
        override fun idejoId() = Unit
    }

/** The inbox's forum menu: the group's forums with their dots, the chats, the directory. */
@HiltViewModel
class InboxMenuViewModel
    @Inject
    constructor(
        private val sessions: SessionManager,
        private val group: GroupForums,
        private val topics: InboxTopics,
        private val chats: InboxChats,
        private val shortcuts: InboxShortcuts,
    ) : ViewModel() {
        /** What the menu reads when it opens: the inbox's chats, the directory. */
        private val read = MutableStateFlow(GroupMenu())

        val state: StateFlow<GroupMenu> =
            combine(sessions.state, group.joined, topics.state, read) {
                app,
                joined,
                news,
                extra,
                ->
                // No forum is the one open: the inbox is.
                extra.copy(forums = yourForums(app.forums, joined, news, current = ""))
            }.stateIn(viewModelScope, SharingStarted.Eagerly, GroupMenu())

        /** The menu opened: every forum's news (SH-12), the chats (SH-13), the directory. */
        fun opened() {
            viewModelScope.launch { topics.refresh() }
            viewModelScope.launch {
                val withNews = chats.withNews()
                read.update { it.copy(chatsWithNews = withNews) }
            }
            viewModelScope.launch {
                val directory = group.hasDirectory()
                read.update { it.copy(directory = directory) }
            }
        }

        /** **Your forums** (SH-12): a forum held, opened. */
        fun enter(forum: YourForum) {
            viewModelScope.launch { sessions.enter(forum.origin) }
        }

        /** Where in the inbox to go (SH-12 to SH-14); its screens take it. */
        fun ask(shortcut: InboxShortcut) = shortcuts.offer(shortcut)

        /** **Remove from app** on a forum of the list (SH-15). */
        fun remove(forum: YourForum) {
            viewModelScope.launch {
                sessions.remove(forum.origin)
                group.refresh()
                topics.refresh()
            }
        }
    }
