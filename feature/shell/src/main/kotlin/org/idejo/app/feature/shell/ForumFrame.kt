// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.idejo.app.core.data.ThemeMode
import org.idejo.app.core.data.TutorialBubbles
import org.idejo.app.core.designsystem.component.Avatar
import org.idejo.app.core.designsystem.component.Crumb
import org.idejo.app.core.designsystem.component.TopBar
import org.idejo.app.core.designsystem.component.Unread
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.designsystem.theme.parseHexColor
import org.idejo.app.core.designsystem.theme.pictureUrl
import org.idejo.app.core.model.Topic

/** Which full-screen page covers the forum. */
private enum class Overlay { None, Sidebar, LogoMenu, UserMenu }

/**
 * The frame of every page of a forum (spec §6): the top bar with the logo, breadcrumbs,
 * **Unread** and the member's picture (SH-01, SH-02), and the full-screen sidebar (SH-30),
 * logo menu (SH-10) and user menu (SH-20) over it. [place] is the page shown; [onNavigate]
 * goes elsewhere; [onSignIn] starts the forum's sign-in (SI-05, SI-09).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumFrame(
    place: Place,
    onNavigate: (Place) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShellViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shell = state ?: return
    val context = LocalContext.current
    var overlay by rememberSaveable { mutableStateOf(Overlay.None) }
    var wholeTitle by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(place) { viewModel.at(place) }
    BackHandler(enabled = overlay != Overlay.None) { overlay = Overlay.None }

    val go: (Place) -> Unit = {
        overlay = Overlay.None
        onNavigate(it)
    }
    val logo = logoOf(shell)
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Box(modifier.fillMaxSize()) {
        Scaffold(
            Modifier.nestedScroll(scroll.nestedScrollConnection),
            topBar = {
                TopBar(
                    crumbs =
                        crumbs(shell, place, go, logo, { overlay = Overlay.LogoMenu }) {
                            wholeTitle = it
                        },
                    onSidebarClick = { overlay = Overlay.Sidebar },
                    unread = unreadOf(shell) { go(Place.Unread) },
                    scrollBehavior = scroll,
                ) {
                    Account(shell, onSignIn) { overlay = Overlay.UserMenu }
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .swipe(right = true) { overlay = Overlay.Sidebar },
            ) { content() }
        }

        Slide(overlay == Overlay.Sidebar, horizontal = true) {
            SidebarPage(shell, sidebarActions(viewModel, context, go)) { overlay = Overlay.None }
        }

        Slide(overlay == Overlay.LogoMenu, horizontal = false) {
            LogoMenu(
                name = shell.name,
                address = shell.address,
                onHome = { go(Place.Home) },
                onShare = { context.shareText(shell.record.origin) },
                onClose = { overlay = Overlay.None },
                logo = logo,
            )
        }

        Slide(overlay == Overlay.UserMenu, horizontal = false) {
            val actions = userMenuActions(viewModel, shell, onSignIn, go, context::openPage)
            UserMenuPage(shell, actions, logo) { overlay = Overlay.None }
        }

        wholeTitle?.let { WholeTitle(it) { wholeTitle = null } }
    }
}

/** The top bar's right end: **Sign in** for a visitor (SI-05), then the member's picture. */
@Composable
private fun Account(
    shell: ShellState,
    onSignIn: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!shell.signedIn) {
            TextButton(onClick = onSignIn) { Text(stringResource(R.string.sign_in)) }
        }
        IconButton(onClick = onOpen) {
            Box(Modifier.padding(4.dp), contentAlignment = Alignment.Center) {
                MemberAvatar(shell, 32.dp)
            }
        }
    }
}

/** The member's picture, their dark one in the dark theme (TH-10), or their initials. */
@Composable
private fun MemberAvatar(
    shell: ShellState,
    size: Dp,
) {
    val me = shell.me
    val dark =
        when (shell.record.themeMode) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
    val url = pictureUrl(me?.avatarUrl, me?.avatarDarkUrl, dark)
    Avatar(
        name = me?.displayName ?: "",
        color = parseHexColor(me?.colour) ?: MaterialTheme.colorScheme.tertiary,
        size = size,
        contentDescription = stringResource(R.string.user_menu_open),
        image =
            url?.let {
                { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            },
    )
}

/** The user menu (SH-20) for the member, or a visitor. */
@Composable
private fun UserMenuPage(
    shell: ShellState,
    actions: UserMenuActions,
    logo: (@Composable () -> Unit)?,
    onClose: () -> Unit,
) {
    val me = shell.me
    UserMenu(
        forumName = shell.name,
        member = me?.let { MemberCard(it.displayName, it.userTag ?: it.username, it.roles) },
        administrator = me?.administrator == true,
        canSignUp =
            shell.record.discovery.account
                ?.signUpUrl != null,
        themeMode = shell.record.themeMode,
        tutorialBubbles = shell.record.tutorialBubbles,
        actions = actions,
        onClose = onClose,
        avatar = { MemberAvatar(shell, 72.dp) },
        logo = logo,
    )
}

/** The sidebar over the whole screen (SH-30): a swipe to the left closes it, as back does. */
@Composable
private fun SidebarPage(
    shell: ShellState,
    actions: SidebarActions,
    onClose: () -> Unit,
) {
    Surface(
        Modifier.fillMaxSize().swipe(right = false, onSwipe = onClose),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp)) {
                Text(
                    stringResource(R.string.sidebar_title),
                    Modifier.weight(1f).padding(12.dp),
                    style = IdejoTheme.typography.titleMedium,
                )
                IconButton(onClick = onClose) {
                    Icon(
                        painterResource(org.idejo.app.core.designsystem.R.drawable.ic_close),
                        stringResource(R.string.close),
                    )
                }
            }
            Sidebar(
                state = shell.sidebar,
                folded = shell.record.folded,
                linksRolled = shell.record.linksRolled,
                signedIn = shell.signedIn,
                openTopic = shell.openTopic,
                actions = actions,
                contentPadding = WindowInsets(bottom = 24.dp).asPaddingValues(),
            )
        }
    }
}

/** A long-pressed topic title, whole (SH-02). */
@Composable
private fun WholeTitle(
    title: String,
    onDismiss: () -> Unit,
) {
    Popup(alignment = Alignment.TopCenter, onDismissRequest = onDismiss) {
        Surface(
            Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()).padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 4.dp,
        ) { Text(title, Modifier.padding(16.dp), style = IdejoTheme.typography.titleMedium) }
    }
}

/** A full-screen page sliding in from the left (the sidebar) or down from the top bar. */
@Composable
private fun Slide(
    visible: Boolean,
    horizontal: Boolean,
    content: @Composable () -> Unit,
) {
    val reduced = IdejoTheme.reducedMotion
    val enter: EnterTransition =
        when {
            reduced -> EnterTransition.None
            horizontal -> slideInHorizontally { -it }
            else -> slideInVertically { -it }
        }
    val exit: ExitTransition =
        when {
            reduced -> ExitTransition.None
            horizontal -> slideOutHorizontally { -it }
            else -> slideOutVertically { -it }
        }
    AnimatedVisibility(visible, enter = enter, exit = exit) { content() }
}

/**
 * A quick swipe to the [right] (or to the left) across the page, not from the screen's
 * edges, which belong to system back (SH-30). A child that scrolls sideways keeps its drags.
 */
private fun Modifier.swipe(
    right: Boolean,
    onSwipe: () -> Unit,
): Modifier =
    pointerInput(right) {
        val edge = EDGE.toPx()
        val distance = SWIPE.toPx()
        var travelled = 0f
        var counted = false
        detectHorizontalDragGestures(
            onDragStart = { start ->
                travelled = 0f
                counted = start.x > edge && start.x < size.width - edge
            },
            onDragEnd = {
                if (counted && (if (right) travelled > distance else travelled < -distance)) {
                    onSwipe()
                }
            },
        ) { _, delta -> travelled += delta }
    }

private val EDGE = 32.dp
private val SWIPE = 72.dp
