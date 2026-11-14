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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import org.idejo.app.feature.onboarding.AccessNotice
import org.idejo.app.feature.onboarding.AccessViewModel
import org.idejo.app.feature.onboarding.rememberSignInAgain
import org.idejo.app.feature.settings.AppSettingsRoute
import org.idejo.app.feature.shell.ForumFrame
import org.idejo.app.feature.shell.Place
import org.idejo.app.feature.shell.UserCardDialog
import org.idejo.app.feature.topic.TopicRoute

/**
 * The open forum: its pages in the frame (spec §6), one back stack per forum, and the cards
 * over them (BR-07, BR-11). The topic page (TV) comes with the next pull requests of M1.
 */
@Composable
fun ForumApp(
    session: ForumSession,
    modifier: Modifier = Modifier,
    access: AccessViewModel = hiltViewModel(),
) {
    // A new back stack for each forum and each sign-in (SessionManager opens a new session).
    val navigation = rememberNavController()
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
            composable<Place.AppSettings> { AppSettingsRoute(Modifier.fillMaxSize()) }
            // A user card (BR-09): a name in a topic opens it.
            dialog<UserCardDialog>(
                dialogProperties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                UserCardRoute(onClose = { navigation.popBackStack() })
            }
        }
        // Over every page; a new one for each forum and each sign-in.
        MemberCards(
            onLink = link,
            viewModel = hiltViewModel(key = "cards:${System.identityHashCode(session)}"),
        )
    }
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
            onTopic = { id, messageId -> navigation.go(Place.Topic(id, messageId)) },
        )
    }
}

/** Home is the start of the back stack; going there again pops back to it. */
private fun NavHostController.go(place: Place) {
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
