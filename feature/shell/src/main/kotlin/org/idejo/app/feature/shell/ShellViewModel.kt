// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.idejo.app.core.api.getOrNull
import org.idejo.app.core.data.Access
import org.idejo.app.core.data.ForumRecord
import org.idejo.app.core.data.ForumSession
import org.idejo.app.core.data.ForumSidebar
import org.idejo.app.core.data.Launch
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.data.SidebarState
import org.idejo.app.core.data.ThemeMode
import org.idejo.app.core.data.TutorialBubbles
import org.idejo.app.core.data.Warnings
import org.idejo.app.core.data.WarningsState
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
) {
    val name: String get() = record.name

    /** The forum's address as people write it: `garden.example.org`. */
    val address: String get() = record.origin.removePrefix("https://").trimEnd('/')
    val signedIn: Boolean get() = access == Access.SignedIn || access is Access.Barred
}

/** The open forum's frame: its sidebar, the member, and the choices kept per forum. */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One per thing the frame's menus and sidebar do.
@HiltViewModel
class ShellViewModel
    @Inject
    constructor(
        private val sessions: SessionManager,
    ) : ViewModel() {
        private val me = MutableStateFlow<Me?>(null)
        private val openTopic = MutableStateFlow<Topic?>(null)
        private val warned = MutableStateFlow<Warnings?>(null)
        private var sidebar: ForumSidebar? = null
        private var reading: Job? = null

        val state: StateFlow<ShellState?> =
            sessions.launch
                .flatMapLatest { launch ->
                    val session = (launch as? Launch.Forum)?.session
                    if (session == null) flowOf(null) else follow(session)
                }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        /** The warnings waiting for the member (TV-11): the card shows while any do. */
        val warnings: StateFlow<WarningsState> =
            warned
                .flatMapLatest { it?.state ?: flowOf(WarningsState()) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, WarningsState())

        private fun follow(session: ForumSession) =
            ForumSidebar(session, viewModelScope).let { opened ->
                sidebar?.close()
                sidebar = opened
                opened.start()
                me.value = null
                warned.value?.close()
                val warnings = Warnings(session, viewModelScope).also { warned.value = it }
                viewModelScope.launch {
                    if (session.access.value == Access.SignedIn) {
                        me.value = session.call { me() }.getOrNull()
                        warnings.start(pending = me.value?.warningPending == true)
                    }
                }
                combine(
                    session.record,
                    session.access,
                    opened.state,
                    me,
                    openTopic,
                ) { record, access, sidebar, me, topic ->
                    ShellState(record, access, sidebar, me, topic)
                }
            }

        /** The page shown changed: the topic open is read for the breadcrumbs. */
        fun at(place: Place) {
            reading?.cancel()
            val id = (place as? Place.Topic)?.id
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

        fun tutorialBubbles(choice: TutorialBubbles) =
            remember { it.copy(tutorialBubbles = choice) }

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
            warned.value?.close()
        }

        private fun remember(change: (ForumRecord) -> ForumRecord) {
            viewModelScope.launch { sessions.updateForum(change) }
        }
    }

private fun Set<String>.toggled(id: String): Set<String> = if (id in this) this - id else this + id
