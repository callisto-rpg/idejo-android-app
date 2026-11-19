// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import org.idejo.app.core.data.ForumSession
import org.idejo.app.core.data.InboxShortcut
import org.idejo.app.core.data.InboxShortcuts
import org.idejo.app.core.data.LinkTarget
import org.idejo.app.core.data.linkTarget
import org.idejo.app.feature.browse.UserCardRoute
import org.idejo.app.feature.inbox.ChatFrameRoute
import org.idejo.app.feature.inbox.InboxPageRoute
import org.idejo.app.feature.inbox.InboxRoute
import org.idejo.app.feature.settings.ProfileAndAccountRoute
import org.idejo.app.feature.shell.ForwardDialog
import org.idejo.app.feature.shell.Place
import org.idejo.app.feature.shell.UserCardDialog
import org.idejo.app.feature.topic.TopicRoute
import javax.inject.Inject

/**
 * idejo ID mode's inbox (MD-02): its tabs (IB-01 to IB-03), and its chats on the topic page
 * without slow mode (IB-06), one back stack, Back from a chat to the tabs. [session] is the
 * inbox's: the topic page and the composer read it as any forum's.
 */
@Composable
fun InboxApp(
    session: ForumSession,
    modifier: Modifier = Modifier,
    navigation: NavHostController = rememberNavController(),
) {
    LaunchedEffect(session) { session.refreshServer() }
    val onTop = rememberLastPlace(session.origin, navigation)
    NavHost(navigation, startDestination = Place.Home, modifier) {
        composable<Place.Home> {
            InboxRoute(onConversation = { navigation.go(Place.Topic(it, private = true)) })
        }
        chatPage(session, navigation, onTop)
        dialog<UserCardDialog>(
            dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            UserCardRoute(
                onClose = { navigation.popBackStack() },
                onConversation = { id ->
                    navigation.popBackStack()
                    navigation.go(Place.Topic(id, private = true))
                },
            )
        }
        forwardCard(navigation)
        inboxSettings(navigation)
    }
    InboxLinks(session, navigation)
    InboxSettingsShortcut(navigation)
}

/** The inbox's **Profile and account**: its settings, for all forums too (ST-07). */
private fun NavGraphBuilder.inboxSettings(navigation: NavHostController) =
    composable<Place.ProfileAndAccount> { entry ->
        InboxPageRoute(
            stringResource(org.idejo.app.feature.inbox.R.string.inbox_profile_and_account),
            onBack = { navigation.go(Place.Home) },
        ) {
            ProfileAndAccountRoute(
                section = entry.toRoute<Place.ProfileAndAccount>().section,
                onOpenSection = { navigation.go(Place.ProfileAndAccount(it)) },
            )
        }
    }

@HiltViewModel
class InboxShortcutViewModel
    @Inject
    constructor(
        val shortcuts: InboxShortcuts,
    ) : ViewModel()

/** **Settings for all forums** from a forum (ST-07): the inbox opens at its settings. */
@Composable
private fun InboxSettingsShortcut(
    navigation: NavHostController,
    viewModel: InboxShortcutViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        if (viewModel.shortcuts.take { it == InboxShortcut.Settings } != null) {
            navigation.go(Place.ProfileAndAccount())
        }
    }
}

/** A chat: the topic page of a conversation, in the inbox's frame. */
private fun NavGraphBuilder.chatPage(
    session: ForumSession,
    navigation: NavHostController,
    onTop: (Place.Topic) -> Unit,
) = composable<Place.Topic> { entry ->
    val here = entry.toRoute<Place.Topic>()
    val back = { navigation.go(Place.Home) }
    val link = rememberLinkOpener(session, navigation, inbox = true)
    ChatFrameRoute(here.id, onBack = back) {
        TopicRoute(
            onUser = { navigation.navigate(UserCardDialog(it)) },
            // Conversations have no tags; links of other kinds open as links.
            onTag = {},
            onLink = link,
            onInlineLink = { mention ->
                val id = mention.id
                if (mention.type == "user" && id != null) navigation.navigate(UserCardDialog(id))
            },
            onTopic = {
                id,
                messageId,
                ->
                navigation.go(Place.Topic(id, messageId, private = true))
            },
            onTop = { onTop(here.copy(messageId = it)) },
            writing =
                rememberTopicWriting(
                    onForward = { id, topicId -> navigation.navigate(ForwardDialog(id, topicId)) },
                    onNewTopic = {},
                ),
        )
    }
}

/** A tapped notification of the inbox (a chat's message) opens its chat (NT-04). */
@Composable
private fun InboxLinks(
    session: ForumSession,
    navigation: NavHostController,
    viewModel: PendingLinksViewModel = hiltViewModel(),
) {
    val pending by viewModel.links.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pending, session) {
        val link = viewModel.links.take(session.origin) ?: return@LaunchedEffect
        when (val target = linkTarget(session, link.url)) {
            is LinkTarget.Topic -> {
                navigation.go(Place.Topic(target.id, target.messageId, private = true))
            }

            else -> {
                navigation.go(Place.Home)
            }
        }
    }
}
