// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.idejo.app.core.data.ForumSession
import org.idejo.app.core.data.LinkTarget
import org.idejo.app.core.data.PendingLinks
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.data.linkTarget
import org.idejo.app.feature.shell.Place
import javax.inject.Inject

/** The address of a notification tapped, waiting for its forum (NT-04). */
@HiltViewModel
class PendingLinksViewModel
    @Inject
    constructor(
        val links: PendingLinks,
        private val sessions: SessionManager,
    ) : ViewModel() {
        /** Back from a topic opened from the inbox's Topics (IB-08). */
        fun backToInbox() {
            viewModelScope.launch { sessions.openInbox() }
        }
    }

/**
 * A notification tapped opens its page here, once its forum is the one on screen (NT-04);
 * so does a topic tapped in the inbox's Topics, and Back from it returns to the inbox
 * (IB-08), not to the forum's home under it.
 */
@Composable
internal fun NotificationLinks(
    session: ForumSession,
    navigation: NavHostController,
    viewModel: PendingLinksViewModel = hiltViewModel(),
) {
    val pending by viewModel.links.pending.collectAsStateWithLifecycle()
    val entry by navigation.currentBackStackEntryAsState()
    var fromInbox by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(pending, session) {
        val link = viewModel.links.take(session.origin) ?: return@LaunchedEffect
        navigation.go(linkTarget(session, link.url).place)
        if (link.fromInbox) fromInbox = navigation.currentBackStackEntry?.id
    }
    BackHandler(fromInbox != null && entry?.id == fromInbox) { viewModel.backToInbox() }
}

private val LinkTarget.place: Place
    get() =
        when (this) {
            LinkTarget.Home -> Place.Home
            LinkTarget.Unread -> Place.Unread
            LinkTarget.Conversations -> Place.Conversations
            is LinkTarget.Topic -> Place.Topic(id, messageId, private = private)
        }
