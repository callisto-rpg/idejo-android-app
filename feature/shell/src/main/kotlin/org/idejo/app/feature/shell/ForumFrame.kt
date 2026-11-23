// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import org.idejo.app.core.designsystem.component.ForumLogo
import org.idejo.app.core.designsystem.component.LocalTourTargets
import org.idejo.app.core.designsystem.component.PullHint
import org.idejo.app.core.designsystem.component.TopBar
import org.idejo.app.core.designsystem.component.TourTargets
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.designsystem.theme.parseHexColor
import org.idejo.app.core.designsystem.theme.pictureUrl
import org.idejo.app.core.model.Topic

/** Which full-screen page covers the forum. */
private enum class Overlay { None, Sidebar, LogoMenu, UserMenu }

/**
 * The frame of every page of a forum (spec §6): the top bar with the logo, breadcrumbs and
 * the member's picture (SH-01, SH-02; **Unread** is a tab of the home page), the full-screen
 * sidebar (SH-30) over it, and the forum menu (SH-10) and the user menu (SH-20) under the bar,
 * both the same way ([MenuPanel]). [place] is
 * the page shown; [onNavigate] goes elsewhere; [onSignIn] starts the forum's sign-in
 * (SI-05, SI-09).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "CyclomaticComplexMethod") // The frame and its layers in one place.
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
    val warnings by viewModel.warnings.collectAsStateWithLifecycle()
    val bubbles by viewModel.bubbles.collectAsStateWithLifecycle()
    val tour by viewModel.tour.collectAsStateWithLifecycle()
    val shell = state ?: return
    val context = LocalContext.current
    var overlay by rememberSaveable { mutableStateOf(Overlay.None) }
    var wholeTitle by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(place) { viewModel.at(place) }
    // The sidebar's back is SidebarFrame's; the menus' is here.
    BackHandler(enabled = overlay == Overlay.LogoMenu || overlay == Overlay.UserMenu) {
        overlay = Overlay.None
    }
    val wide = wideWindow()
    val fold = currentFold()
    val docked = wide || fold != null

    val go: (Place) -> Unit = {
        overlay = Overlay.None
        onNavigate(it)
    }
    val logo = logoOf(shell)

    // The menus under the top bar follow the finger as the sidebar does (Sliding).
    val scope = rememberCoroutineScope()
    val reduced by rememberUpdatedState(IdejoTheme.reducedMotion)
    val forumMenu =
        remember {
            Sliding(scope, { reduced }, { overlay = Overlay.LogoMenu }, {
                if (overlay == Overlay.LogoMenu) overlay = Overlay.None
            })
        }
    val userMenu =
        remember {
            Sliding(scope, { reduced }, { overlay = Overlay.UserMenu }, {
                if (overlay == Overlay.UserMenu) overlay = Overlay.None
            })
        }
    val menuOpen = overlay == Overlay.LogoMenu
    LaunchedEffect(menuOpen) { if (menuOpen) viewModel.logoMenuOpened() }
    var frameTop by remember { mutableFloatStateOf(0f) }
    var barBottom by remember { mutableFloatStateOf(0f) }

    // The onboarding tour (IX-18): over everything, once the warnings are read.
    val tourTargets = remember { TourTargets() }
    val onTourTopic = (place as? Place.Topic)?.id == tour.onboarding?.topicId
    val step =
        tourStep(tour, onTourTopic, docked || overlay == Overlay.Sidebar)
            .takeIf { shell.signedIn && warnings.warnings.isEmpty() }
    // The last card points at All categories, at the top of the sidebar.
    LaunchedEffect(tour.finished) { if (tour.finished && !docked) overlay = Overlay.Sidebar }
    // Pulling down the forum menu ends it, as Close does.
    LaunchedEffect(tour.finished, forumMenu.showing) {
        if (tour.finished && forumMenu.showing) viewModel.dismissTour()
    }
    // Going elsewhere (All categories) ends it, as Close does.
    LaunchedEffect(place, tour.finished) {
        if (tour.finished &&
            !onTourTopic
        ) {
            viewModel.dismissTour()
        }
    }

    CompositionLocalProvider(LocalTourTargets provides tourTargets.takeIf { step != null }) {
        Box(modifier.fillMaxSize().onGloballyPositioned { frameTop = it.positionInRoot().y }) {
            Box(Modifier.fillMaxSize()) {
                SidebarFrame(
                    wide = wide,
                    fold = fold,
                    open = overlay == Overlay.Sidebar,
                    onOpen = { overlay = Overlay.Sidebar },
                    onClose = { overlay = Overlay.None },
                    onPull = { if (tour.finished) viewModel.dismissTour() },
                    topBar = {
                        var account by remember { mutableStateOf(Rect.Zero) }
                        var bar by remember { mutableStateOf(Offset.Zero) }
                        TopBar(
                            crumbs =
                                crumbs(shell, place, go, logo, {
                                    if (!docked) overlay = Overlay.Sidebar
                                }) { wholeTitle = it },
                            modifier =
                                Modifier
                                    .onGloballyPositioned {
                                        bar = it.positionInRoot()
                                        barBottom = it.boundsInRoot().bottom
                                    }.pullDown { at ->
                                        // From the member's picture, their menu.
                                        if (account.contains(at + bar)) userMenu else forumMenu
                                    },
                            // The home page says the forum menu is a pull down (SH-10); the
                            // hint goes down with the pull and fades out.
                            pullHint =
                                PullHint(
                                    stringResource(R.string.top_bar_pull_hint),
                                    stringResource(R.string.top_bar_pull_hint_short),
                                    // Down with whichever menu the pull brings.
                                    pulled = {
                                        maxOf(forumMenu.shown.value, userMenu.shown.value)
                                    },
                                ).takeIf { place == Place.Home },
                        ) {
                            Account(
                                shell,
                                onSignIn,
                                onOpen = { overlay = Overlay.UserMenu },
                                modifier =
                                    Modifier.onGloballyPositioned {
                                        account = it.boundsInRoot()
                                    },
                            )
                        }
                    },
                    sidebar = { docked ->
                        SidebarPage(shell, logo, sidebarActions(viewModel, context, go), docked)
                    },
                    pageGlass = { maxOf(forumMenu.fraction, userMenu.fraction) },
                    page = {
                        Column(Modifier.fillMaxSize()) {
                            // A saved copy on screen while the forum cannot be reached (OF-02).
                            OfflineBar(shell.savedCopy)
                            // In the page's flow, so it never covers the composer (NT-07).
                            PushBar(
                                offered = shell.pushBarOffered,
                                bubble = shell.tutorialBubbles == TutorialBubbles.All,
                                place = place,
                                onDismiss = viewModel::dismissPushBar,
                            )
                            Box(Modifier.weight(1f)) { content() }
                        }
                    },
                )
            }

            // Over the page, under the menus (NT-01).
            LiveBubbleStack(
                bubbles,
                onOpen = { go(viewModel.open(it)) },
                onDismiss = viewModel::dismiss,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .safeDrawingPadding()
                        .padding(start = 12.dp, end = 12.dp, top = BUBBLES_TOP),
            )

            val menuTop =
                with(LocalDensity.current) { (barBottom - frameTop).coerceAtLeast(0f).toDp() }
            MenuPanel(
                forumMenu,
                open = menuOpen,
                top = menuTop,
                tag = FORUM_MENU,
                onClose = { overlay = Overlay.None },
            ) {
                LogoMenu(
                    name = shell.name,
                    address = shell.address,
                    // Home, on Categories: any way home starts there.
                    onHome = { go(Place.Home) },
                    onShare = { context.shareText(shell.record.origin) },
                    logo = logo,
                    // The sidebar's button lives here, not in the top bar (SH-30); docked, none.
                    onSidebar = if (docked) null else ({ overlay = Overlay.Sidebar }),
                    chatService =
                        shell.record.discovery.community
                            ?.chatUrl != null,
                    group = shell.group.takeIf { shell.idejoId },
                    actions = logoMenuActions(viewModel, context, go),
                )
            }

            MenuPanel(
                userMenu,
                open = overlay == Overlay.UserMenu,
                top = menuTop,
                tag = USER_MENU,
                onClose = { overlay = Overlay.None },
            ) {
                val actions = userMenuActions(viewModel, shell, onSignIn, go, context::openPage)
                UserMenuPage(shell, actions, logo)
            }

            wholeTitle?.let { WholeTitle(it) { wholeTitle = null } }

            // Over any page, as the web's (TV-11).
            if (shell.signedIn && warnings.warnings.isNotEmpty()) {
                WarningCard(warnings, warningActions(viewModel, go))
            }

            step?.let {
                TourOverlay(
                    step = it,
                    tour = tour,
                    name = shell.me?.displayName.orEmpty(),
                    allLabel = stringResource(allLabel(shell.record.linksRolled)),
                    targets = tourTargets,
                    actions =
                        TourActions(
                            onSwipe = { overlay = Overlay.Sidebar },
                            onOpenTopic = {
                                tour.onboarding?.let { o ->
                                    go(Place.Topic(o.topicId))
                                }
                            },
                            onClose = {
                                viewModel.dismissTour()
                                overlay = Overlay.None
                            },
                        ),
                )
            }
        }
    }
}

/** What the sidebar's top link reads: **All** rolled up, else **All categories**. */
private fun allLabel(rolled: Boolean): Int =
    if (rolled) R.string.sidebar_all else R.string.sidebar_all_categories

/** The warning card's actions (TV-11). */
private fun warningActions(
    viewModel: ShellViewModel,
    go: (Place) -> Unit,
) = WarningActions(
    onUnderstand = viewModel::acknowledgeWarnings,
    onMessage = { viewModel.openWarned(it, go) },
    onRules = { go(Place.Rules) },
)

/** The top bar's right end: **Sign in** for a visitor (SI-05), then the member's picture. */
@Composable
private fun Account(
    shell: ShellState,
    onSignIn: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
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
    logo: String?,
) {
    val me = shell.me
    UserMenu(
        forumName = shell.name,
        member = me?.let { MemberCard(it.displayName, it.username, it.roles) },
        administrator = me?.administrator == true,
        canSignUp =
            shell.record.discovery.account
                ?.signUpUrl != null,
        themeMode = shell.record.themeMode,
        tutorialBubbles = shell.tutorialBubbles,
        actions = actions,
        avatar = { MemberAvatar(shell, 72.dp) },
        logo = logo,
        profileAndAccount = shell.signedIn && me != null,
    )
}

/**
 * The sidebar: over the whole screen with its title and close button (SH-30), or [docked]
 * beside the page in an expanded window (SH-60), where it is always open.
 */
@Composable
private fun SidebarPage(
    shell: ShellState,
    logo: String?,
    actions: SidebarActions,
    docked: Boolean,
) {
    Surface(
        Modifier.fillMaxSize().glassEdge(enabled = !docked),
        // Over the page, frosted glass while the trial lasts (SidebarGlass).
        color = glassBackground(opaque = docked),
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(if (docked) Modifier else Modifier.safeDrawingPadding()) {
            if (!docked) SidebarHead(shell.name, logo)
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

/**
 * The sidebar's head over the page (SH-30): the forum's logo and name (no idejo button,
 * steward request, 2026-11-23). Back, or a swipe to the left, closes the sidebar.
 */
@Composable
private fun SidebarHead(
    name: String,
    logo: String?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TARGET + 8.dp)
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ForumLogo(name, size = HEAD_LOGO, logoUrl = logo)
        Text(
            name,
            Modifier.weight(1f).semantics { heading() },
            style = IdejoTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

/** The height of the sidebar head's forum logo. */
private val HEAD_LOGO = 34.dp

/** The least height of a touch target (AX). */
private val TARGET = 48.dp

/** Under the top bar. */
private val BUBBLES_TOP = 64.dp
