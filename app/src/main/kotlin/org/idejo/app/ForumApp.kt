// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.idejo.app.core.data.Access
import org.idejo.app.core.data.ForumSession
import org.idejo.app.feature.browse.CategoryRoute
import org.idejo.app.feature.browse.HelpRoute
import org.idejo.app.feature.browse.HomeRoute
import org.idejo.app.feature.browse.MemberCards
import org.idejo.app.feature.browse.RulesRoute
import org.idejo.app.feature.browse.SearchRoute
import org.idejo.app.feature.browse.TagsRoute
import org.idejo.app.feature.browse.UnreadRoute
import org.idejo.app.feature.browse.UserCardRoute
import org.idejo.app.feature.composer.ForwardRoute
import org.idejo.app.feature.composer.NewTopicRoute
import org.idejo.app.feature.composer.ShareViewModel
import org.idejo.app.feature.inbox.ConversationsRoute
import org.idejo.app.feature.onboarding.AccessNotice
import org.idejo.app.feature.onboarding.AccessViewModel
import org.idejo.app.feature.onboarding.rememberSignInAgain
import org.idejo.app.feature.shell.ForumFrame
import org.idejo.app.feature.shell.ForwardDialog
import org.idejo.app.feature.shell.InlineCardDialog
import org.idejo.app.feature.shell.Place
import org.idejo.app.feature.shell.ShareDialog
import org.idejo.app.feature.shell.UserCardDialog
import org.idejo.app.feature.topic.InlineCardRoute
import org.idejo.app.feature.topic.TopicRoute

/**
 * The open forum: its pages in the frame (spec §6), one back stack per forum, and the cards
 * over them (BR-07, BR-11). A new back stack ([navigation]) for each forum and each sign-in
 * (SessionManager opens a new session each time).
 */
@Composable
fun ForumApp(
    session: ForumSession,
    modifier: Modifier = Modifier,
    navigation: NavHostController = rememberNavController(),
    access: AccessViewModel = hiltViewModel(),
) {
    val entry by navigation.currentBackStackEntryAsState()
    val signIn = rememberSignInAgain(access)
    LaunchedEffect(session) { session.refreshServer() }
    val context = LocalContext.current
    val link: (String) -> Unit = { context.openLink(session.origin, it) }
    ForumFrame(
        // A dialog (a user card) keeps the page under it in the breadcrumbs.
        place = entry?.place() ?: navigation.previousBackStackEntry?.place() ?: Place.Home,
        onNavigate = { navigation.go(it) },
        onSignIn = signIn,
        modifier = modifier,
    ) {
        NavHost(navigation, startDestination = Place.Home) {
            val topic: (String) -> Unit = { navigation.go(Place.Topic(it)) }
            val tag: (String) -> Unit = { navigation.go(Place.Tags(it)) }
            composable<Place.Home> {
                Page(session, signIn) {
                    HomeRoute(
                        onCategory = { navigation.go(Place.Category(it)) },
                        onTopic = topic,
                        onTag = tag,
                    )
                }
            }
            composable<Place.Category> {
                Page(session, signIn) { CategoryRoute(onTopic = topic, onTag = tag) }
            }
            topicPage(navigation, link) { content -> Page(session, signIn, content) }
            composable<Place.Unread> {
                Page(session, signIn) { UnreadRoute(onTopic = topic, onTag = tag) }
            }
            composable<Place.Tags> { Page(session, signIn) { TagsRoute(onTopic = topic) } }
            composable<Place.Search> {
                Page(session, signIn) {
                    SearchRoute(onMessage = {
                        id,
                        message,
                        ->
                        navigation.go(Place.Topic(id, message))
                    })
                }
            }
            composable<Place.Rules> { Page(session, signIn) { RulesRoute(onLink = link) } }
            composable<Place.Help> { Page(session, signIn) { HelpRoute() } }
            addForumPages(navigation)
            profileAndAccountPage(navigation) { content -> Page(session, signIn, content) }
            conversationsPage(navigation) { content -> Page(session, signIn, content) }
            newTopicPage(navigation) { content -> Page(session, signIn, content) }
            cards(navigation)
        }
        // Over every page; a new one for each forum and each sign-in.
        MemberCards(
            onLink = link,
            viewModel = hiltViewModel(key = "cards:${System.identityHashCode(session)}"),
        )
        SharedImagesCard(session, navigation)
        NotificationLinks(session, navigation)
    }
}

/** The cards over a page: a user's, an inline link's, the forward card. */
private fun NavGraphBuilder.cards(navigation: NavHostController) {
    // A user card (BR-09): a name in a topic opens it.
    dialog<UserCardDialog>(
        dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val context = LocalContext.current
        UserCardRoute(
            onClose = { navigation.popBackStack() },
            onConversation = { id ->
                navigation.popBackStack()
                navigation.go(Place.Topic(id, private = true))
            },
            // The chat service's link outside idejo ID mode (PC-02): absolute, https.
            onLink = { context.openLink(it, it) },
            onIdejoId = {
                navigation.popBackStack()
                navigation.go(Place.IdejoId)
            },
        )
    }
    inlineCard(navigation)
    forwardCard(navigation)
    shareCard(navigation)
}

/** The forward card (CO-17): the target chosen opens with the message over its composer. */
internal fun NavGraphBuilder.forwardCard(navigation: NavHostController) =
    dialog<ForwardDialog> { entry ->
        ForwardRoute(
            onPick = { target ->
                val forward = entry.toRoute<ForwardDialog>().messageId
                navigation.popBackStack()
                navigation.go(Place.Topic(target.id, forward = forward))
            },
        )
    }

/** An inline link's card (TV-22): each way out closes it first. */
private fun NavGraphBuilder.inlineCard(navigation: NavHostController) =
    dialog<InlineCardDialog>(
        dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val leave: (Place) -> Unit = {
            navigation.popBackStack()
            navigation.go(it)
        }
        InlineCardRoute(
            onTopic = { leave(Place.Topic(it)) },
            onCategory = { leave(Place.Category(it)) },
            onTag = { leave(Place.Tags(it)) },
            onUser = {
                navigation.popBackStack()
                navigation.navigate(UserCardDialog(it))
            },
            onClose = { navigation.popBackStack() },
        )
    }

/** The topic page (TV); a name opens its user card, a tag Filter by tag. */
private fun NavGraphBuilder.topicPage(
    navigation: NavHostController,
    link: (String) -> Unit,
    page: @Composable (@Composable () -> Unit) -> Unit,
) = composable<Place.Topic> {
    page {
        TopicRoute(
            onUser = { navigation.navigate(UserCardDialog(it)) },
            onTag = { navigation.go(Place.Tags(it)) },
            onLink = link,
            onInlineLink = { mention ->
                val id = mention.id
                if (mention.type == "user") {
                    id?.let { navigation.navigate(UserCardDialog(it)) }
                } else {
                    navigation.navigate(
                        InlineCardDialog(
                            mention.type,
                            mention.text,
                            id,
                            mention.slug,
                            mention.name,
                        ),
                    )
                }
            },
            onTopic = { id, messageId -> navigation.go(Place.Topic(id, messageId)) },
            writing =
                rememberTopicWriting(
                    onForward = { id, topicId -> navigation.navigate(ForwardDialog(id, topicId)) },
                    onNewTopic = { start ->
                        navigation.go(
                            Place.NewTopic(start.categoryId, start.from, start.title, start.body),
                        )
                    },
                ),
        )
    }
}

/** **Private Conversations** (PC-01): a card opens its conversation. */
private fun NavGraphBuilder.conversationsPage(
    navigation: NavHostController,
    page: @Composable (@Composable () -> Unit) -> Unit,
) = composable<Place.Conversations> {
    page {
        ConversationsRoute(onConversation = { navigation.go(Place.Topic(it, private = true)) })
    }
}

/** **New topic** (CO-19): the topic started replaces it. */
private fun NavGraphBuilder.newTopicPage(
    navigation: NavHostController,
    page: @Composable (@Composable () -> Unit) -> Unit,
) = composable<Place.NewTopic> {
    page {
        NewTopicRoute(
            onOpen = { id ->
                navigation.popBackStack()
                navigation.go(Place.Topic(id))
            },
            onDone = { navigation.popBackStack() },
        )
    }
}

/** Home is the start of the back stack; going there again pops back to it. */
internal fun NavHostController.go(place: Place) {
    if (place == Place.Home) {
        popBackStack(Place.Home, inclusive = false)
    } else {
        navigate(place) { launchSingleTop = true }
    }
}

private fun NavBackStackEntry.place(): Place? =
    when {
        destination.hasRoute<Place.Category>() -> toRoute<Place.Category>()
        destination.hasRoute<Place.Topic>() -> toRoute<Place.Topic>()
        destination.hasRoute<Place.Tags>() -> toRoute<Place.Tags>()
        destination.hasRoute<Place.NewTopic>() -> toRoute<Place.NewTopic>()
        destination.hasRoute<Place.ProfileAndAccount>() -> toRoute<Place.ProfileAndAccount>()
        else -> SIMPLE.firstOrNull { destination.hasRoute(it::class) }
    }

private val SIMPLE =
    listOf(
        Place.Home,
        Place.Unread,
        Place.Search,
        Place.Rules,
        Place.Help,
        Place.AppSettings,
        Place.Conversations,
        Place.IdejoId,
        Place.AddForum,
    )

/**
 * A page under the forum's SI-09 and SI-10 notices; without [content], a page still to come
 * in M1.
 */
@Composable
private fun Page(
    session: ForumSession,
    signIn: () -> Unit,
    content: (@Composable () -> Unit)? = null,
) {
    val state by session.access.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            AccessNotice(state, session.name, onSignInAgain = signIn)
        }
        if (content != null) {
            Box(Modifier.weight(1f)) { content() }
        } else {
            Text(
                stringResource(R.string.page_pending),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
