// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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
import org.idejo.app.core.data.openAt
import org.idejo.app.core.designsystem.theme.parseHexColor
import org.idejo.app.feature.shell.Place
import org.idejo.app.feature.shell.UserCardDialog
import javax.inject.Inject

/** The address of a notification tapped, waiting for its forum (NT-04). */
@HiltViewModel
class PendingLinksViewModel
    @Inject
    constructor(
        val links: PendingLinks,
        private val sessions: SessionManager,
    ) : ViewModel() {
        /**
         * DL-03: [url] is on another forum held, or the inbox: it opens there, the app
         * switching to it. False for an address on none of them.
         */
        suspend fun elsewhere(url: String): Boolean = sessions.openAt(url, links)

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
        navigation.open(linkTarget(session, link.url))
        if (link.fromInbox) fromInbox = navigation.currentBackStackEntry?.id
    }
    BackHandler(fromInbox != null && entry?.id == fromInbox) { viewModel.backToInbox() }
}

/** The page of [target] (DL-02); an address the app has no page for, the home page. */
internal fun NavHostController.open(target: LinkTarget) {
    when (target) {
        LinkTarget.Home, LinkTarget.External -> {
            go(Place.Home)
        }

        LinkTarget.Unread -> {
            go(Place.Unread)
        }

        LinkTarget.Conversations -> {
            go(Place.Conversations)
        }

        is LinkTarget.Category -> {
            go(Place.Category(target.id))
        }

        is LinkTarget.User -> {
            navigate(UserCardDialog(target.id))
        }

        is LinkTarget.Topic -> {
            go(
                Place.Topic(target.id, target.messageId, private = target.private),
            )
        }
    }
}

/**
 * A link in a message or a forum page (DL-03): on [session]'s forum, its page here
 * (DL-02); on another forum held or the inbox, there; anything else, and paths the app has
 * no page for, in a Custom Tab with the forum's toolbar colour.
 */
@Composable
internal fun rememberLinkOpener(
    session: ForumSession,
    navigation: NavHostController,
    inbox: Boolean = false,
    viewModel: PendingLinksViewModel = hiltViewModel(),
): (String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(session, navigation) {
        { url ->
            val absolute = absoluteLink(session.origin, url)
            scope.launch {
                when (val target = linkTarget(session, absolute, inbox)) {
                    LinkTarget.External -> {
                        if (!viewModel.elsewhere(absolute)) {
                            context.openLink(session.origin, absolute, toolbarOf(session))
                        }
                    }

                    else -> {
                        navigation.open(target)
                    }
                }
            }
        }
    }
}

/** The forum's primary colour, for its Custom Tabs' toolbar (DL-03). */
private fun toolbarOf(session: ForumSession): Int? =
    parseHexColor(
        session.server.value
            ?.theme
            ?.primary,
    )?.toArgb()
