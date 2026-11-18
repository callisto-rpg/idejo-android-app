// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.idejo.app.core.data.LiveBubble
import org.idejo.app.core.data.NotificationKind
import org.idejo.app.core.designsystem.component.Bubble
import org.idejo.app.core.designsystem.theme.BubbleKind

/**
 * The web's live bubbles at the top (NT-01), newest on top: each goes after 10 s, the
 * time paused while touched; a tap opens the message, a swipe or × dismisses it. TalkBack
 * reads each once, as it appears.
 */
@Composable
internal fun LiveBubbleStack(
    bubbles: List<LiveBubble>,
    onOpen: (LiveBubble) -> Unit,
    onDismiss: (LiveBubble) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.widthIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        bubbles.forEach { bubble ->
            key(bubble.key) { TimedBubble(bubble, onOpen, onDismiss) }
        }
    }
}

@Composable
private fun TimedBubble(
    bubble: LiveBubble,
    onOpen: (LiveBubble) -> Unit,
    onDismiss: (LiveBubble) -> Unit,
) {
    var touched by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState { onDismiss(bubble) }
    LaunchedEffect(bubble.key) {
        var left = LIFETIME_MS
        while (left > 0) {
            delay(TICK_MS)
            if (!touched) left -= TICK_MS
        }
        dismiss()
    }
    val swipe =
        rememberSwipeToDismissBoxState(
            confirmValueChange = {
                if (it != SwipeToDismissBoxValue.Settled) dismiss()
                true
            },
        )
    SwipeToDismissBox(
        swipe,
        backgroundContent = {},
        modifier =
            Modifier
                .semantics { liveRegion = LiveRegionMode.Polite }
                .pointerInput(bubble.key) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        touched = true
                        waitForUpOrCancellation(PointerEventPass.Initial)
                        touched = false
                    }
                },
    ) {
        Bubble(
            kind = bubble.kind.look,
            title = bubbleTitle(bubble),
            text = bubble.excerpt.orEmpty(),
            onClick = { onOpen(bubble) },
            onDismiss = dismiss,
            icon =
                bubble.emojiUrl?.let { url ->
                    { AsyncImage(url, null, Modifier.size(18.dp)) }
                },
        )
    }
}

/** The web's sentence: "Mina mentioned you in “Tomatoes”". */
@Composable
private fun bubbleTitle(bubble: LiveBubble): String {
    val text =
        when (bubble.kind) {
            NotificationKind.Mention -> R.string.bubble_mention
            NotificationKind.Reply -> R.string.bubble_reply
            NotificationKind.RoleMention -> R.string.bubble_role_mention
            NotificationKind.Reaction -> R.string.bubble_reaction
            NotificationKind.Followed -> R.string.bubble_followed
        }
    return stringResource(text, bubble.authorName, bubble.topicTitle)
}

private val NotificationKind.look: BubbleKind
    get() =
        when (this) {
            NotificationKind.Followed -> BubbleKind.Followed
            NotificationKind.RoleMention -> BubbleKind.RoleMention
            NotificationKind.Reply -> BubbleKind.Reply
            NotificationKind.Mention -> BubbleKind.Mention
            NotificationKind.Reaction -> BubbleKind.Reaction
        }

private const val LIFETIME_MS = 10_000L
private const val TICK_MS = 100L
