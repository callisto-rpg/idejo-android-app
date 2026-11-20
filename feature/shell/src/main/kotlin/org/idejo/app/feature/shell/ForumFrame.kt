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
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import org.idejo.app.core.designsystem.component.TopBar
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.designsystem.theme.parseHexColor
import org.idejo.app.core.designsystem.theme.pictureUrl
import org.idejo.app.core.model.Topic
import kotlin.math.abs
import org.idejo.app.core.designsystem.R as DesignR

/** Which full-screen page covers the forum. */
private enum class Overlay { None, Sidebar, LogoMenu, UserMenu }

/**
 * The frame of every page of a forum (spec §6): the top bar with the logo, breadcrumbs and
 * the member's picture (SH-01, SH-02; **Unread** is a tab of the home page), and the
 * full-screen sidebar (SH-30), logo menu (SH-10) and user menu (SH-20) over it. [place] is
 * the page shown; [onNavigate] goes elsewhere; [onSignIn] starts the forum's sign-in
 * (SI-05, SI-09).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod") // The frame and its layers, over the page, in one place.
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
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // The menus' glass: what is behind them blurs as they come in (SidebarGlass).
    val menuIn by animateFloatAsState(
        if (overlay == Overlay.LogoMenu || overlay == Overlay.UserMenu) 1f else 0f,
        if (IdejoTheme.reducedMotion) snap() else spring(),
        label = "menu glass",
    )
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().glassBehind { menuIn }) {
            SidebarFrame(
                wide = wide,
                fold = fold,
                open = overlay == Overlay.Sidebar,
                onOpen = { overlay = Overlay.Sidebar },
                onClose = { overlay = Overlay.None },
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
                                .onGloballyPositioned { bar = it.positionInRoot() }
                                .pullDown { at ->
                                    // From the member's picture, their menu; else the forum's.
                                    overlay =
                                        if (account.contains(at + bar)) {
                                            Overlay.UserMenu
                                        } else {
                                            Overlay.LogoMenu
                                        }
                                },
                        scrollBehavior = scroll,
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
                    SidebarPage(shell, logo, sidebarActions(viewModel, context, go), docked) {
                        overlay = Overlay.LogoMenu
                    }
                },
                modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
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

        Slide(overlay == Overlay.LogoMenu, horizontal = false) {
            LaunchedEffect(Unit) { viewModel.logoMenuOpened() }
            LogoMenu(
                name = shell.name,
                address = shell.address,
                // Home, on Categories: any way home starts there.
                onHome = { go(Place.Home) },
                onShare = { context.shareText(shell.record.origin) },
                onClose = { overlay = Overlay.None },
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

        Slide(overlay == Overlay.UserMenu, horizontal = false) {
            val actions = userMenuActions(viewModel, shell, onSignIn, go, context::openPage)
            UserMenuPage(shell, actions, logo) { overlay = Overlay.None }
        }

        wholeTitle?.let { WholeTitle(it) { wholeTitle = null } }

        // Over any page, as the web's (TV-11).
        if (shell.signedIn && warnings.warnings.isNotEmpty()) {
            WarningCard(warnings, warningActions(viewModel, go))
        }
    }
}

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
        tutorialBubbles = shell.tutorialBubbles,
        actions = actions,
        onClose = onClose,
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
    onMenu: () -> Unit,
) {
    Surface(
        Modifier.fillMaxSize(),
        // Over the page, frosted glass while the trial lasts (SidebarGlass).
        color = glassBackground(opaque = docked),
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(if (docked) Modifier else Modifier.safeDrawingPadding()) {
            if (!docked) SidebarHead(shell.name, logo, onMenu)
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
 * The sidebar's head over the page (SH-30): the forum's logo and name, and at the right the
 * idejo logo, which opens the forum menu. Back, or a swipe to the left, closes the sidebar.
 */
@Composable
private fun SidebarHead(
    name: String,
    logo: String?,
    onMenu: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
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
        // The idejo button, as tall as the forum's logo, in a 48 dp target (AX).
        val button = painterResource(R.drawable.ic_idejo)
        Image(
            button,
            stringResource(DesignR.string.top_bar_forum_menu, name),
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onMenu)
                .padding(horizontal = 4.dp, vertical = (TARGET - HEAD_LOGO) / 2)
                .height(HEAD_LOGO)
                .aspectRatio(button.intrinsicSize.width / button.intrinsicSize.height),
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

/**
 * A pull down that starts on this (the top bar, SH-01) calls [onPull] with where it started:
 * seen before the bar's own drag, which hides and shows it, so the pull always wins.
 */
private fun Modifier.pullDown(onPull: (Offset) -> Unit): Modifier =
    pointerInput(Unit) {
        val far = PULL.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var moved = Offset.Zero
            // Watched until let go, pulled down, or dragged sideways.
            var watching = true
            while (watching) {
                val change =
                    awaitPointerEvent(PointerEventPass.Initial)
                        .changes
                        .firstOrNull { it.id == down.id }
                moved += change?.positionChange() ?: Offset.Zero
                val pulled = moved.y > far && moved.y > abs(moved.x)
                if (pulled) {
                    change?.consume()
                    onPull(down.position)
                }
                watching = change?.pressed == true && !pulled && abs(moved.x) <= far
            }
        }
    }

/** How far down a pull goes before it opens a menu. */
private val PULL = 24.dp

/** The height of the sidebar head's forum logo and idejo button, the same. */
private val HEAD_LOGO = 34.dp

/** The least height of a touch target (AX). */
private val TARGET = 48.dp

/** Under the top bar. */
private val BUBBLES_TOP = 64.dp
