// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.idejo.app.core.data.TopicState
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.markdown.IdejoMarkdown
import org.idejo.app.core.markdown.plainText
import org.idejo.app.core.model.Message

/** Where the jump button leads (SH-04). */
data class JumpActions(
    val onFirst: () -> Unit = {},
    val onUnread: () -> Unit = {},
    val onLast: () -> Unit = {},
    val onMessage: (String) -> Unit = {},
)

/**
 * The jump button's targets, as the web's navigator: First message, First unread (when
 * there is something unread), Last message, My last message (when the member wrote here),
 * then the moderators' pinned messages.
 */
internal data class Jumps(
    val unread: Boolean,
    val mine: String?,
    val pins: List<Message>,
)

internal fun jumpsOf(state: TopicUi): Jumps {
    val topic = state.topic
    return Jumps(
        unread = topic.unread != null || (topic.topic?.unreadCount ?: 0) > 0,
        mine = topic.topic?.myLastMessageId,
        pins = state.pins,
    )
}

/**
 * Each jump: on the page when the message is in the window (a scroll), else the window
 * loads around it ([TopicActions.onJump], [TopicActions.onFirstUnread], [TopicActions.onEnd]).
 */
@Composable
internal fun rememberJumpActions(
    topic: TopicState,
    list: LazyListState,
    actions: TopicActions,
): JumpActions {
    val scope = rememberCoroutineScope()

    fun scroll(index: Int) = scope.launch { list.animateScrollToItem(index) }
    return JumpActions(
        onFirst = { scroll(1) },
        onUnread = {
            if (topic.unread != null) scroll(indexOf(topic, "unread")) else actions.onFirstUnread()
        },
        onLast = { if (topic.atEnd) scroll(lastIndex(topic)) else actions.onEnd() },
        onMessage = { id ->
            val index = indexOf(topic, id)
            if (index > 0) scroll(index) else actions.onJump(id)
        },
    )
}

/** Whether the window has room for the jump card beside the topic (SH-60: ≥ 1200 dp). */
@Composable
internal fun jumpCardBeside(): Boolean {
    val width = LocalWindowInfo.current.containerSize.width
    return with(LocalDensity.current) { width.toDp() } >= BESIDE
}

/** The round button at the top right, once the title has scrolled away (SH-04). */
@Composable
internal fun JumpButton(
    jumps: Jumps,
    actions: JumpActions,
    modifier: Modifier = Modifier,
) = Box(modifier.padding(12.dp)) {
    var open by remember { mutableStateOf(false) }
    FilledTonalIconButton(onClick = { open = true }) {
        Icon(painterResource(R.drawable.ic_unfold_more), stringResource(R.string.jump_open))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        JumpEntries(jumps, actions) { open = false }
    }
}

/** The navigator as a card beside the topic, in a wide window (SH-60). */
@Composable
internal fun JumpCard(
    title: String,
    jumps: Jumps,
    actions: JumpActions,
    modifier: Modifier = Modifier,
) = Surface(
    modifier.width(CARD_WIDTH).fillMaxHeight().padding(12.dp),
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Column(Modifier.padding(8.dp).verticalScroll(rememberScrollState())) {
        Text(
            title,
            Modifier.padding(8.dp).semantics { heading() },
            style = IdejoTheme.typography.titleMedium,
        )
        JumpEntries(jumps, actions) {}
    }
}

@Composable
private fun JumpEntries(
    jumps: Jumps,
    actions: JumpActions,
    then: () -> Unit,
) = Column {
    Entry(R.string.jump_first) { then().also { actions.onFirst() } }
    if (jumps.unread) Entry(R.string.jump_unread) { then().also { actions.onUnread() } }
    Entry(R.string.jump_last) { then().also { actions.onLast() } }
    jumps.mine?.let { id -> Entry(R.string.jump_mine) { then().also { actions.onMessage(id) } } }
    if (jumps.pins.isNotEmpty()) {
        HorizontalDivider()
        Text(
            stringResource(R.string.jump_pinned),
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = IdejoTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        jumps.pins.forEach { pin -> Pin(pin) { then().also { actions.onMessage(pin.id) } } }
    }
}

@Composable
private fun Entry(
    label: Int,
    onClick: () -> Unit,
) = DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)

/** A pinned message: its author and first words (the web's 60 characters). */
@Composable
private fun Pin(
    pin: Message,
    onClick: () -> Unit,
) {
    val spoiler = stringResource(org.idejo.app.core.markdown.R.string.markdown_spoiler)
    val markdown = pin.bodyMd.orEmpty()
    val words =
        remember(markdown, spoiler) {
            val plain = IdejoMarkdown.parse(markdown).plainText(spoiler).trim()
            if (plain.length > PIN_WORDS) plain.take(PIN_WORDS).trimEnd() + "…" else plain
        }
    DropdownMenuItem(
        text = {
            Text(
                pin.author.displayName + " · " + words,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = { Icon(painterResource(R.drawable.ic_keep), null) },
        onClick = onClick,
    )
}

private val BESIDE = 1200.dp
private val CARD_WIDTH = 280.dp
private const val PIN_WORDS = 60
