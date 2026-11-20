// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.idejo.app.core.api.GifSearch
import org.idejo.app.core.data.Access
import org.idejo.app.core.data.AppChoices
import org.idejo.app.core.data.Drafts
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.data.TutorialBubbles
import org.idejo.app.core.model.Me
import javax.inject.Inject

/**
 * The composer of the topic page (CO): the route's `id` is the topic. It lives as long as the
 * page's back-stack entry, as the topic channel does (CO-21): going to the background closes
 * the open message after 30 s on the server, and the app does not fight it (invariant 7).
 */
@HiltViewModel
class ComposerViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        sessions: SessionManager,
        choices: AppChoices,
        http: OkHttpClient,
        private val shared: SharedImages,
        drafts: Drafts,
    ) : ViewModel() {
        private val session = sessions.active?.takeIf { it.access.value == Access.SignedIn }
        val topicId: String = checkNotNull(savedState["id"])

        /** Opened from the share sheet's card: the shared pictures go in (CO-14). */
        private val share: Boolean = savedState["share"] ?: false

        fun takeShared(): List<ImageFile> = if (share) shared.take() else emptyList()

        /** Null for visitors: no composer. */
        val composer: Composer? =
            session?.let {
                Composer(
                    ApiComposerBackend.of(it, topicId),
                    viewModelScope,
                    gifSearch = { provider -> GifSearch.of(http, provider) },
                    draft = drafts.slot(it.origin, Drafts.topic(topicId)),
                )
            }

        /** The author, drawn on the composer's head (CO-05), and their choices (ST-02). */
        val me: StateFlow<Me?> = session?.me?.state ?: MutableStateFlow(null)

        /** **Tutorial bubbles**, one choice for the whole app (CO-06, SH-21). */
        val bubbles: StateFlow<TutorialBubbles> =
            choices.tutorialBubbles.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                TutorialBubbles.All,
            )

        /** The forum's address, for pictures. */
        val origin: String = session?.origin.orEmpty()

        init {
            // DL-04: shared text goes after what the box holds, once the composer is read.
            val text = if (share) shared.takeText() else null
            composer?.refresh { text?.let { composer.typed(after(composer.state.value.text, it)) } }
            // OF-04: what waited for the forum goes when it answers again.
            session?.let { forum ->
                viewModelScope.launch {
                    forum.offline
                        .drop(1)
                        .filter { !it }
                        .collect { composer?.backOnline() }
                }
            }
            // A message forwarded here from the forward card (CO-17), once.
            val forward: String? = savedState["forward"]
            if (forward != null && savedState.get<Boolean>(FORWARDED) != true) {
                savedState[FORWARDED] = true
                composer?.forward(forward)
            }
            session?.let { forum ->
                // The forum's one copy of `GET /me`: a choice changed in Profile and account
                // reaches this composer at once (ST-02).
                viewModelScope.launch { forum.me.load() }
            }
        }
    }

private const val FORWARDED = "forwarded"

/** [place]'s draft in [origin], for the composer and the new-topic form (OF-03). */
fun Drafts.slot(
    origin: String,
    place: String,
): DraftSlot =
    object : DraftSlot {
        override fun read() = get(origin, place)

        override fun write(text: String) = put(origin, place, text)
    }

/** [shared] after what the box holds (DL-04). */
internal fun after(
    box: String,
    shared: String,
): String = box.trimEnd().let { if (it.isEmpty()) shared else "$it $shared" }
