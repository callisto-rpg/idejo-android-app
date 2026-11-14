// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.idejo.app.core.data.ThemeMode
import org.idejo.app.core.data.TutorialBubbles
import org.idejo.app.core.designsystem.component.Crumb
import org.idejo.app.core.designsystem.component.Unread
import org.idejo.app.core.designsystem.component.dot
import org.idejo.app.core.designsystem.component.unreadLevel
import org.idejo.app.core.designsystem.theme.parseHexColor
import org.idejo.app.core.model.Topic

/** The forum's logo from discovery, on the forum's own host, when it has one. */
internal fun logoOf(shell: ShellState): (@Composable () -> Unit)? {
    val url =
        shell.record.discovery.logoUrl
            ?.let {
                shell.record.origin
                    .toHttpUrlOrNull()
                    ?.resolve(it)
            }?.toString() ?: return null
    return { AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
}

/** **Unread** with its count and colour (SH-01), for a member signed in. */
internal fun unreadOf(
    shell: ShellState,
    onClick: () -> Unit,
): Unread? =
    if (shell.signedIn) {
        Unread(shell.sidebar.unread.count, unreadLevel(shell.sidebar.unread.level), onClick)
    } else {
        null
    }

internal fun Place.label(): Int =
    when (this) {
        Place.Unread -> R.string.place_unread
        Place.Tags -> R.string.place_tags
        Place.Search -> R.string.place_search
        Place.Rules -> R.string.place_rules
        Place.Help -> R.string.place_help
        Place.AppSettings -> R.string.place_app_settings
        Place.Home, is Place.Category, is Place.Topic -> R.string.sidebar_all_categories
    }

internal fun sidebarActions(
    viewModel: ShellViewModel,
    context: android.content.Context,
    go: (Place) -> Unit,
): SidebarActions =
    object : SidebarActions {
        override fun open(place: Place) = go(place)

        override fun fold(categoryId: String) = viewModel.fold(categoryId)

        override fun rollLinks(rolled: Boolean) = viewModel.rollLinks(rolled)

        override fun follow(
            topic: Topic,
            followed: Boolean,
        ) = viewModel.follow(topic, followed)

        override fun unfollowAndMute(topic: Topic) = viewModel.unfollowAndMute(topic)

        override fun markUnread(topic: Topic) = viewModel.markUnread(topic)

        override fun copyLink(topic: Topic) {
            topic.url?.let { context.copyLink(it) }
        }

        override fun share(topic: Topic) {
            topic.url?.let { context.shareText(it) }
        }

        override fun retry() = viewModel.refresh()
    }

internal fun userMenuActions(
    viewModel: ShellViewModel,
    shell: ShellState,
    onSignIn: () -> Unit,
    go: (Place) -> Unit,
    openPage: (String) -> Unit,
): UserMenuActions =
    object : UserMenuActions {
        override fun themeMode(mode: ThemeMode) = viewModel.themeMode(mode)

        override fun tutorialBubbles(choice: TutorialBubbles) = viewModel.tutorialBubbles(choice)

        override fun appSettings() = go(Place.AppSettings)

        // SH-24: the forum's admin panel, on the web.
        override fun admin() = openPage(shell.record.origin.trimEnd('/') + "/admin")

        override fun signIn() = onSignIn()

        override fun signUp() {
            shell.record.discovery.account
                ?.signUpUrl
                ?.let(openPage)
        }

        override fun signOut() = viewModel.signOut()

        override fun remove() = viewModel.remove()
    }

/** SH-02: the logo (it opens the logo menu), the category's tile, the page's title. */
@Composable
internal fun crumbs(
    shell: ShellState,
    place: Place,
    go: (Place) -> Unit,
    logo: (@Composable () -> Unit)?,
    openLogoMenu: () -> Unit,
    showWhole: (String) -> Unit,
): List<Crumb> {
    val forum = Crumb.Forum(shell.name, onClick = openLogoMenu, logo = logo)
    return when (place) {
        Place.Home -> {
            listOf(forum)
        }

        is Place.Category -> {
            val category = shell.sidebar.category(place.id)
            listOfNotNull(
                forum,
                category?.let { categoryCrumb(it, go) },
                category?.let { Crumb.Page(it.title) },
            )
        }

        is Place.Topic -> {
            val topic = shell.openTopic?.takeIf { it.id == place.id }
            val category = shell.sidebar.category(topic?.categoryId)
            listOfNotNull(
                forum,
                category?.let { categoryCrumb(it, go) },
                topic?.let { Crumb.Page(it.title, onLongClick = { showWhole(it.title) }) },
            )
        }

        else -> {
            listOf(forum, Crumb.Page(stringResource(place.label())))
        }
    }
}

@Composable
internal fun categoryCrumb(
    category: org.idejo.app.core.model.Category,
    go: (Place) -> Unit,
): Crumb.Category =
    Crumb.Category(
        name = category.title,
        color = parseHexColor(category.colour) ?: MaterialTheme.colorScheme.secondary,
        onClick = { go(Place.Category(category.id)) },
    )
