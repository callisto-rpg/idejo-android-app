// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.idejo.app.core.data.AppChoices
import org.idejo.app.core.data.GroupForums
import org.idejo.app.core.data.InboxChats
import org.idejo.app.core.data.InboxShortcut
import org.idejo.app.core.data.InboxShortcuts
import org.idejo.app.core.data.InboxTopics
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.data.ThemeMode
import org.idejo.app.core.data.TutorialBubbles
import org.idejo.app.core.designsystem.component.Avatar
import org.idejo.app.core.designsystem.component.LocalTopBarMenu
import org.idejo.app.core.designsystem.component.TopBarMenu
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.designsystem.theme.parseHexColor
import org.idejo.app.core.designsystem.theme.pictureUrl
import org.idejo.app.core.model.Me
import javax.inject.Inject

/**
 * The group's inbox with the forum menu (SH-10, steward request 2026-11-23): a pull down on
 * any top bar of [content] brings [InboxMenu] under it with the finger, as in a forum
 * ([MenuPanel]), so the forum menu can be pulled down wherever the member is. Its shortcuts
 * go to the inbox's tabs ([onHome], which they read from `InboxShortcuts`); **By address** is
 * [onAddForum]. From the member's picture, a tap or a pull down brings the user menu (SH-20,
 * steward bug report 2026-11-24), the inbox's: **Profile and account** is
 * [onProfileAndAccount], **App settings** [onAppSettings].
 */
@Suppress("LongMethod") // The two menus and the bar's gestures, as the forum's frame.
@Composable
fun InboxMenuFrame(
    onHome: () -> Unit,
    onAddForum: () -> Unit,
    modifier: Modifier = Modifier,
    onProfileAndAccount: () -> Unit = {},
    onAppSettings: () -> Unit = {},
    viewModel: InboxMenuViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val menu by viewModel.state.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    var userOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val reduced by rememberUpdatedState(IdejoTheme.reducedMotion)
    val sliding = remember { Sliding(scope, { reduced }, { open = true }, { open = false }) }
    val userSliding =
        remember { Sliding(scope, { reduced }, { userOpen = true }, { userOpen = false }) }
    LaunchedEffect(open) { if (open) viewModel.opened() }
    BackHandler(enabled = open || userOpen) {
        open = false
        userOpen = false
    }
    var frameTop by remember { mutableFloatStateOf(0f) }
    var barBottom by remember { mutableFloatStateOf(0f) }
    var bar by remember { mutableStateOf(Offset.Zero) }
    var account by remember { mutableStateOf(Rect.Zero) }
    val menuOver =
        remember(sliding, userSliding) {
            TopBarMenu(
                pull =
                    Modifier
                        .onGloballyPositioned {
                            bar = it.positionInRoot()
                            barBottom = it.boundsInRoot().bottom
                        }.pullDown { at ->
                            // From the member's picture, their menu.
                            if (account.contains(at + bar)) userSliding else sliding
                        },
                // The page under the bar blurs as a menu comes in, as a forum's.
                below = Modifier.glassBehind { maxOf(sliding.fraction, userSliding.fraction) },
                account =
                    Modifier
                        .onGloballyPositioned { account = it.boundsInRoot() }
                        .clip(CircleShape)
                        .clickable { userOpen = true },
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
        val userActions =
            inboxUserActions(viewModel, context, user.origin, {
                userOpen = false
                onProfileAndAccount()
            }) {
                userOpen = false
                onAppSettings()
            }
        MenuPanel(
            userSliding,
            open = userOpen,
            top = top,
            tag = USER_MENU,
            onClose = { userOpen = false },
        ) { InboxUserMenu(user, userActions) }
    }
}

/** The inbox's user menu (SH-20): the forum's, without **Accounts** and **Sign out**. */
@Composable
private fun InboxUserMenu(
    user: InboxUser,
    actions: UserMenuActions,
) {
    val me = user.me
    UserMenu(
        forumName = user.name,
        member = me?.let { MemberCard(it.displayName, it.username, it.roles) },
        administrator = me?.administrator == true,
        canSignUp = false,
        themeMode = user.themeMode,
        tutorialBubbles = user.tutorialBubbles,
        actions = actions,
        avatar = {
            val url = pictureUrl(me?.avatarUrl, me?.avatarDarkUrl, IdejoTheme.dark)
            Avatar(
                name = me?.displayName.orEmpty(),
                color = parseHexColor(me?.colour) ?: MaterialTheme.colorScheme.tertiary,
                size = 72.dp,
                contentDescription = null,
                image =
                    url?.let {
                        {
                            AsyncImage(
                                it,
                                null,
                                Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    },
            )
        },
        logo = user.logoUrl,
        accounts = false,
    )
}

private fun inboxUserActions(
    viewModel: InboxMenuViewModel,
    context: Context,
    origin: String,
    profileAndAccount: () -> Unit,
    appSettings: () -> Unit,
): UserMenuActions =
    object : UserMenuActions {
        override fun themeMode(mode: ThemeMode) = viewModel.themeMode(mode)

        override fun tutorialBubbles(choice: TutorialBubbles) = viewModel.tutorialBubbles(choice)

        override fun profileAndAccount() = profileAndAccount()

        override fun appSettings() = appSettings()

        // SH-24: the inbox's admin panel, on the web.
        override fun admin() = context.openPage(origin.trimEnd('/') + "/admin")

        // Signed in, always: the inbox is idejo ID mode's own.
        override fun signIn() = Unit

        override fun signUp() = Unit

        override fun signOut() = Unit

        override fun remove() = Unit
    }

/** What the inbox's user menu shows: its member, name, logo and the member's choices. */
data class InboxUser(
    val origin: String = "",
    val name: String = "",
    val logoUrl: String? = null,
    val me: Me? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val tutorialBubbles: TutorialBubbles = TutorialBubbles.All,
)

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
        private val choices: AppChoices,
    ) : ViewModel() {
        private val inbox = sessions.inbox

        /** The inbox's user menu (SH-20): its member, read when the inbox opens. */
        val user: StateFlow<InboxUser> =
            combine(
                inbox?.me?.state ?: flowOf(null),
                inbox?.record ?: flowOf(null),
                choices.tutorialBubbles,
            ) { me, record, bubbles ->
                InboxUser(
                    origin = record?.origin.orEmpty(),
                    name = record?.name.orEmpty(),
                    logoUrl =
                        record?.discovery?.logoUrl?.let {
                            record.origin
                                .toHttpUrlOrNull()
                                ?.resolve(it)
                                ?.toString()
                        },
                    me = me,
                    themeMode = record?.themeMode ?: ThemeMode.System,
                    tutorialBubbles = bubbles,
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, InboxUser())

        /** **Theme** (TH-03): kept on the inbox's record, as a forum's on its own. */
        fun themeMode(mode: ThemeMode) {
            val origin = inbox?.origin ?: return
            viewModelScope.launch { sessions.updateForum(origin) { it.copy(themeMode = mode) } }
        }

        /** **Tutorial bubbles**, for the whole app (SH-21). */
        fun tutorialBubbles(choice: TutorialBubbles) {
            viewModelScope.launch { choices.tutorialBubbles(choice) }
        }

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
