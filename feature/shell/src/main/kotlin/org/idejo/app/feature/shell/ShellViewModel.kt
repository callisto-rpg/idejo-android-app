// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.api.getOrNull
import org.idejo.app.core.data.Access
import org.idejo.app.core.data.AppChoices
import org.idejo.app.core.data.AppMode
import org.idejo.app.core.data.ConversationsState
import org.idejo.app.core.data.ForumConversations
import org.idejo.app.core.data.ForumRecord
import org.idejo.app.core.data.ForumSession
import org.idejo.app.core.data.ForumSidebar
import org.idejo.app.core.data.GroupForums
import org.idejo.app.core.data.InboxChats
import org.idejo.app.core.data.InboxShortcut
import org.idejo.app.core.data.InboxShortcuts
import org.idejo.app.core.data.InboxTopics
import org.idejo.app.core.data.Launch
import org.idejo.app.core.data.LiveBubble
import org.idejo.app.core.data.LiveBubbles
import org.idejo.app.core.data.NotificationKind
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.data.SidebarState
import org.idejo.app.core.data.ThemeMode
import org.idejo.app.core.data.TutorialBubbles
import org.idejo.app.core.data.Warnings
import org.idejo.app.core.data.WarningsState
import org.idejo.app.core.designsystem.component.LogoNews
import org.idejo.app.core.model.Me
import org.idejo.app.core.model.Topic
import javax.inject.Inject

/** Everything the frame shows around a page. */
@Immutable
data class ShellState(
    val record: ForumRecord,
    val access: Access,
    val sidebar: SidebarState = SidebarState(),
    /** The signed-in member (`GET /me`); null for a visitor or until read. */
    val me: Me? = null,
    /** The topic open, for the breadcrumbs and the sidebar (SH-02, SH-34). */
    val openTopic: Topic? = null,
    /** The app's **Tutorial bubbles** choice (SH-21), the same in every forum. */
    val tutorialBubbles: TutorialBubbles = TutorialBubbles.All,
    /** **Private Conversations** at the sidebar's foot (PC-01, SH-36). */
    val conversations: ConversationsState = ConversationsState(),
    /** The forum sends pushes (`GET /server`'s `settings.push`). */
    val pushOn: Boolean = false,
    /** **Dismiss** was said to the notifications bar in the last 14 days (NT-07). */
    val pushBarQuiet: Boolean = false,
    /** idejo ID mode (MD-02): the logo menu leads to the group's forums. */
    val idejoId: Boolean = false,
    /** A forum joined since last seen waits to be connected: the logo's dot (SI-07). */
    val newForums: Boolean = false,
    /** idejo ID mode's part of the logo menu (SH-12 to SH-14). */
    val group: GroupMenu = GroupMenu(),
    /** News in the member's other forums: the logo's dot (SH-03). */
    val news: LogoNews? = null,
    /** When the saved copy shown was read, while the forum cannot be reached (OF-02). */
    val savedCopy: Long? = null,
) {
    val name: String get() = record.name

    /** The forum's address as people write it: `garden.example.org`. */
    val address: String get() = record.origin.removePrefix("https://").trimEnd('/')
    val signedIn: Boolean get() = access == Access.SignedIn || access is Access.Barred

    /**
     * The notifications bar may be offered (NT-07), as the web's banner: push on, the
     * welcome card seen, a topic followed (writing in one follows it), not dismissed lately.
     * Whether Android may already notify is the bar's own question.
     */
    val pushBarOffered: Boolean
        get() =
            access == Access.SignedIn && pushOn && me?.welcomed == true &&
                sidebar.topics.isNotEmpty() && !pushBarQuiet
}

/** The open forum's frame: its sidebar, the member, and the choices kept per forum. */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One per thing the frame's menus and sidebar do.
@HiltViewModel
class ShellViewModel
    @Inject
    constructor(
        private val sessions: SessionManager,
        private val choices: AppChoices,
        private val group: GroupForums,
        private val topics: InboxTopics,
        private val chats: InboxChats,
        private val shortcuts: InboxShortcuts,
    ) : ViewModel() {
        /** What the logo menu reads when it opens: the inbox's chats, the directory. */
        private val menuRead = MutableStateFlow(GroupMenu())
        private val openTopic = MutableStateFlow<Topic?>(null)
        private val warned = MutableStateFlow<Warnings?>(null)
        private val live = MutableStateFlow<LiveBubbles?>(null)
        private var sidebar: ForumSidebar? = null
        private var conversations: ForumConversations? = null
        private var reading: Job? = null

        val state: StateFlow<ShellState?> =
            sessions.launch
                .flatMapLatest { launch ->
                    val session = (launch as? Launch.Forum)?.session
                    if (session == null) flowOf(null) else follow(session)
                }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        init {
            // SH-03: while a forum is open in idejo ID mode, the others are read again, as the
            // web's community dot asks the group's service.
            viewModelScope.launch {
                sessions.launch
                    .combine(sessions.state.map { it.mode == AppMode.IdejoId }) { launch, idejoId ->
                        launch is Launch.Forum && idejoId
                    }.distinctUntilChanged()
                    .collectLatest { reading ->
                        while (reading) {
                            topics.refresh()
                            delay(NEWS_EVERY)
                        }
                    }
            }
        }

        /** The warnings waiting for the member (TV-11): the card shows while any do. */
        val warnings: StateFlow<WarningsState> =
            warned
                .flatMapLatest { it?.state ?: flowOf(WarningsState()) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, WarningsState())

        /** The live bubbles at the top (NT-01). */
        val bubbles: StateFlow<List<LiveBubble>> =
            live
                .flatMapLatest { it?.state ?: flowOf(emptyList()) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        private fun follow(session: ForumSession) =
            ForumSidebar(session, viewModelScope).let { opened ->
                sidebar?.close()
                sidebar = opened
                opened.start()
                conversations?.close()
                val private = ForumConversations(session, viewModelScope).also { it.start() }
                conversations = private
                warned.value?.close()
                val warnings = Warnings(session, viewModelScope).also { warned.value = it }
                live.value?.close()
                live.value =
                    LiveBubbles(session, viewModelScope).also {
                        it.onScreen = openTopic.value?.id
                        it.start()
                    }
                viewModelScope.launch {
                    // The forum's one copy of `GET /me` (MeRepository), read afresh on opening.
                    val me = session.me.refresh()?.getOrNull()
                    if (me != null) warnings.start(pending = me.warningPending)
                }
                combine(
                    session.record,
                    session.access,
                    opened.state,
                    session.me.state,
                    openTopic,
                ) { record, access, sidebar, me, topic ->
                    ShellState(record, access, sidebar, me, topic)
                }.combine(choices.tutorialBubbles) { shell, bubbles ->
                    shell.copy(tutorialBubbles = bubbles)
                }.combine(private.state) { shell, private ->
                    shell.copy(conversations = private)
                }.combine(session.server) { shell, server ->
                    shell.copy(pushOn = server?.settings?.push == true)
                }.combine(choices.pushBarQuiet()) { shell, quiet ->
                    shell.copy(pushBarQuiet = quiet)
                }.combine(sessions.state.map { it.mode == AppMode.IdejoId }) { shell, idejoId ->
                    shell.copy(idejoId = idejoId)
                }.combine(group.anyNew) { shell, new ->
                    shell.copy(newForums = new)
                }.combine(
                    combine(sessions.state, group.joined, topics.state, menuRead) {
                        app,
                        joined,
                        read,
                        extra,
                        ->
                        extra.copy(forums = yourForums(app.forums, joined, read, session.origin))
                    },
                ) { shell, menu -> shell.copy(group = menu) }
                    .combine(topics.state) { shell, read ->
                        val news = logoNews(read.levels, session.origin)
                        shell.copy(news = news.takeIf { shell.idejoId })
                    }.combine(session.savedCopy) { shell, at -> shell.copy(savedCopy = at) }
            }

        /** The page shown changed: the topic open is read for the breadcrumbs. */
        fun at(place: Place) {
            reading?.cancel()
            val id = (place as? Place.Topic)?.id
            live.value?.onScreen = id
            if (id == null) {
                openTopic.value = null
                return
            }
            if (openTopic.value?.id == id) return
            openTopic.value =
                sidebar
                    ?.state
                    ?.value
                    ?.topics
                    ?.firstOrNull { it.id == id }
            val session = sessions.active ?: return
            reading =
                viewModelScope.launch {
                    session.call { topic(id) }.getOrNull()?.let { openTopic.value = it }
                }
        }

        fun refresh() {
            sidebar?.refresh()
            conversations?.refresh()
        }

        /**
         * The logo menu opened in idejo ID mode: every connected forum's news (the dots,
         * SH-12), the inbox's chats (SH-13) and whether the group has a directory (SH-14).
         */
        fun logoMenuOpened() {
            if (state.value?.idejoId != true) return
            viewModelScope.launch { topics.refresh() }
            viewModelScope.launch {
                val withNews = chats.withNews()
                menuRead.update { it.copy(chatsWithNews = withNews) }
            }
            viewModelScope.launch {
                val directory = group.hasDirectory()
                menuRead.update { it.copy(directory = directory) }
            }
        }

        /** **Your forums** (SH-12): another forum held, opened; this one stays. */
        fun enter(forum: YourForum) {
            if (forum.current) return
            viewModelScope.launch { sessions.enter(forum.origin) }
        }

        /** The inbox at [shortcut] (SH-12 to SH-14): Connect, its tabs, the directory. */
        fun openInbox(shortcut: InboxShortcut) {
            shortcuts.offer(shortcut)
            viewModelScope.launch { sessions.openInbox() }
        }

        /** **Remove from app** on a forum of the list (SH-15). */
        fun removeForum(forum: YourForum) {
            viewModelScope.launch {
                sessions.remove(forum.origin)
                // This forum's frame is gone when it was the one removed.
                group.refresh()
                topics.refresh()
            }
        }

        /** A bubble swiped away, closed with ×, or its time over (NT-01). */
        fun dismiss(bubble: LiveBubble) {
            live.value?.dismiss(bubble.key)
        }

        /**
         * A bubble tapped (NT-01): where the member left off in the topic, as the web's
         * `?unread`; a reaction, the message reacted to.
         */
        fun open(bubble: LiveBubble): Place {
            dismiss(bubble)
            val private =
                state.value
                    ?.conversations
                    ?.conversations
                    .orEmpty()
                    .any { it.id == bubble.topicId }
            val message = bubble.messageId.takeIf { bubble.kind == NotificationKind.Reaction }
            return Place.Topic(bubble.topicId, message, private = private)
        }

        /** **I understand** on the warning card, or the card closed (TV-11). */
        fun acknowledgeWarnings() {
            warned.value?.acknowledge()
        }

        /** **The message** on the warning card: opened in the topic it is in now. */
        fun openWarned(
            messageId: String,
            open: (Place) -> Unit,
        ) {
            val warnings = warned.value ?: return
            viewModelScope.launch {
                warnings.topicOf(messageId)?.let { open(Place.Topic(it, messageId)) }
            }
        }

        /** The fold arrow beside a category (SH-33), remembered per forum. */
        fun fold(categoryId: String) = remember { it.copy(folded = it.folded.toggled(categoryId)) }

        /** The ^ and V beside **All categories** (SH-35), remembered per forum. */
        fun rollLinks(rolled: Boolean) = remember { it.copy(linksRolled = rolled) }

        fun themeMode(mode: ThemeMode) = remember { it.copy(themeMode = mode) }

        /** **Dismiss** on the notifications bar, or Android's question refused (NT-07). */
        fun dismissPushBar() {
            viewModelScope.launch { choices.dismissPushBar() }
        }

        fun tutorialBubbles(choice: TutorialBubbles) {
            viewModelScope.launch { choices.tutorialBubbles(choice) }
        }

        fun follow(
            topic: Topic,
            followed: Boolean,
        ) {
            sidebar?.follow(topic, followed)
            openTopic.update { if (it?.id == topic.id) it.copy(followed = followed) else it }
        }

        fun unfollowAndMute(topic: Topic) {
            sidebar?.unfollowAndMute(topic)
            openTopic.update {
                if (it?.id == topic.id) it.copy(followed = false, muted = true) else it
            }
        }

        fun markUnread(topic: Topic) {
            sidebar?.markUnread(topic)
        }

        /** **Sign out** (SI-11): the forum stays, as a visitor. */
        fun signOut() {
            val origin = sessions.active?.origin ?: return
            viewModelScope.launch { sessions.signOut(origin) }
        }

        /** **Remove from app**, under Accounts: signed out, then the welcome screen. */
        fun remove() {
            val origin = sessions.active?.origin ?: return
            viewModelScope.launch { sessions.remove(origin) }
        }

        override fun onCleared() {
            sidebar?.close()
            conversations?.close()
            warned.value?.close()
            live.value?.close()
        }

        private fun remember(change: (ForumRecord) -> ForumRecord) {
            viewModelScope.launch { sessions.updateForum(transform = change) }
        }
    }

private fun Set<String>.toggled(id: String): Set<String> = if (id in this) this - id else this + id
