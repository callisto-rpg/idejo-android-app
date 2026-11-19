// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.data.Glow
import org.idejo.app.core.data.TopicState
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Emoji
import org.idejo.app.core.model.Mention
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.Reactor
import org.idejo.app.core.model.Tag
import org.idejo.app.core.model.Topic

/** Where the topic page leads: a member's card, a tag's topics, a link. */
data class TopicActions(
    val onUser: (String) -> Unit = {},
    val onTag: (String) -> Unit = {},
    val onLink: (String) -> Unit = {},
    val onInlineLink: (Mention) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onEarlier: () -> Unit = {},
    val onLater: () -> Unit = {},
    val onEnd: () -> Unit = {},
    /** The jump button's First unread, when the window does not hold it (SH-04). */
    val onFirstUnread: () -> Unit = {},
    /** A jump to a message the window does not hold (SH-04). */
    val onJump: (String) -> Unit = {},
    val onSeen: (String) -> Unit = {},
    /** The first message on screen, as the member scrolls: the place kept (MD-05). */
    val onTop: (String) -> Unit = {},
    val onUnreadShown: () -> Unit = {},
    val onFollow: (Boolean) -> Unit = {},
    val onMessage: (String) -> Unit = {},
    val onTopic: (String) -> Unit = {},
    val onVote: (String, String, List<String>) -> Unit = { _, _, _ -> },
    /** A reaction by its emoji's name, added or taken back (MA-03). */
    val onReact: (String, String, Boolean) -> Unit = { _, _, _ -> },
    val reactors: suspend (String) -> List<Reactor> = { emptyList() },
    val searchEmoji: suspend (String) -> List<Emoji> = { emptyList() },
    /** A report and its reason; true once sent (MA-04). */
    val onReport: suspend (String, String) -> Boolean = { _, _ -> false },
)

/**
 * The topic page (TV-01 to TV-12); [onUser] opens a user card, [onTag] Filter by tag,
 * [onTopic] another topic, at a message when one is given.
 */
@Composable
fun TopicRoute(
    onUser: (String) -> Unit,
    onTag: (String) -> Unit,
    onLink: (String) -> Unit,
    onInlineLink: (Mention) -> Unit,
    onTopic: (String, String?) -> Unit,
    writing: TopicWriting,
    viewModel: TopicViewModel = hiltViewModel(),
    /** The first message on screen, kept as the place to come back to (MD-05). */
    onTop: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val here = state.topic.topic?.id
    TopicScreen(
        state,
        TopicActions(
            onUser = onUser,
            onTag = onTag,
            onLink = onLink,
            onInlineLink = onInlineLink,
            onRetry = { viewModel.retry() },
            onEarlier = { viewModel.earlier() },
            onLater = { viewModel.later() },
            onEnd = { viewModel.end() },
            onFirstUnread = { viewModel.jump(null) },
            onJump = { viewModel.jump(it) },
            onSeen = { viewModel.seen(it) },
            onTop = onTop,
            onUnreadShown = { viewModel.unreadShown() },
            onFollow = { viewModel.follow(it) },
            onMessage = { id ->
                scope.launch {
                    viewModel.reach(id)?.takeIf { it != here }?.let { onTopic(it, id) }
                }
            },
            onTopic = { onTopic(it, null) },
            onVote = { message, poll, options -> viewModel.vote(message, poll, options) },
            onReact = viewModel::react,
            reactors = viewModel::reactors,
            searchEmoji = viewModel::searchEmoji,
            onReport = viewModel::report,
        ),
        writing = writing,
    )
}

/**
 * The header, the opening message on top, then the window: **N earlier messages**, the
 * messages with the red unread line, **N later messages**; **N new messages** and **Jump to
 * the end to write** float while the window does not reach the end (TV-01, TV-05).
 */
@Composable
fun TopicScreen(
    state: TopicUi,
    actions: TopicActions,
    modifier: Modifier = Modifier,
    writing: TopicWriting = TopicWriting(),
    list: LazyListState = rememberLazyListState(),
) {
    val topic = state.topic
    var viewed by remember { mutableStateOf<ViewedImage?>(null) }
    val jumps = jumpsOf(state)
    val jump = rememberJumpActions(topic, list, actions)
    val beside = jumpCardBeside() && topic.topic != null
    val scrolled by remember(list) { derivedStateOf { list.firstVisibleItemIndex > 0 } }
    Row(modifier.fillMaxSize().onKeyEvent { completes(it, writing) }) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Page(state, actions, writing, list) { viewed = it }
            if (!beside && scrolled && topic.topic != null) {
                JumpButton(jumps, jump, Modifier.align(Alignment.TopEnd))
            }
        }
        if (beside) JumpCard(topic.topic?.title.orEmpty(), jumps, jump)
    }
    viewed?.let { ImageViewer(it) { viewed = null } }
}

/** The page's content, and what floats over its bottom: a problem, or the way down. */
@Composable
private fun Page(
    state: TopicUi,
    actions: TopicActions,
    writing: TopicWriting,
    list: LazyListState,
    onImage: (ViewedImage) -> Unit,
) = Box(Modifier.fillMaxSize()) {
    val topic = state.topic
    when {
        topic.gone -> Gone()
        topic.topic == null && topic.loading -> Spinner()
        topic.topic == null -> topic.problem?.let { Problem(it, actions.onRetry) }
        else -> Messages(state, actions, writing, list, onImage)
    }
    val bottom = Modifier.align(Alignment.BottomCenter)
    val problem = topic.problem
    when {
        topic.topic != null && problem != null -> {
            ProblemBar(problem, actions.onRetry, bottom)
        }

        !topic.atEnd && !topic.loading -> {
            Floating(topic.newBelow, actions.onEnd, bottom)
        }
    }
}

@Composable
private fun Messages(
    state: TopicUi,
    actions: TopicActions,
    writing: TopicWriting,
    list: LazyListState,
    onImage: (ViewedImage) -> Unit,
) {
    val topic = state.topic
    val open = topic.topic?.let { !it.locked && !it.archived } == true
    var actionsOpen by rememberActionsOpen(list)
    val close = { actionsOpen = null }
    val context =
        MessageContext(
            state.origin,
            state.emoji,
            onUser = actions.onUser,
            onLink = actions.onLink,
            onInlineLink = actions.onInlineLink,
            onImage = onImage,
            onMessage = actions.onMessage,
            onTopic = actions.onTopic,
            onVote = actions.onVote,
            mayVote = open && state.signedIn,
            voting = topic.voting,
            me = state.me,
            actions = messageActions(state, actions, writing, open, close),
            actionsOpen = actionsOpen,
            onTapMessage = { actionsOpen = it },
        )
    val glowing = rememberGlowing(list, state.glows)
    LazyColumn(
        Modifier.fillMaxSize().imePadding(),
        state = list,
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item(key = "header") { Header(topic.topic!!, actions, state.continuedFrom) }
        topic.opening?.let { opening ->
            item(key = opening.id) {
                MessageItem(opening, context)
                HorizontalDivider()
            }
        }
        if (topic.earlier > 0 || topic.loadingEarlier) {
            item(key = "earlier") {
                More(topic.earlier, earlier = true, topic.loadingEarlier, actions.onEarlier)
            }
        }
        topic.messages.forEach { message ->
            if (message.id == topic.unread?.messageId) {
                item(key = "unread") { UnreadLine(topic.unread!!.count) }
            }
            item(key = message.id) { Entry(message, context, glowing, writing) }
        }
        if (topic.later > 0 || topic.loadingLater) {
            item(
                key = "later",
            ) { More(topic.later, earlier = false, topic.loadingLater, actions.onLater) }
        }
        if (composerAtEnd(topic, writing)) item(key = "composer") { writing.box(Modifier) }
    }
    Landing(topic, list)
    Following(topic, list, actions)
}

/** A message, glowing when it names the member (TV-07), or the composer in its place. */
@Composable
private fun Entry(
    message: Message,
    context: MessageContext,
    glowing: SnapshotStateMap<String, Glow>,
    writing: TopicWriting,
) {
    val glow = glowing[message.id]
    when {
        writing.mayWrite && message.id == writing.openMessageId -> writing.box(Modifier)
        glow == null -> MessageItem(message, context)
        else -> GlowingMessage(message, context, glow) { glowing.remove(message.id) }
    }
}

/** Scrolls to the message the window was loaded around, once it is there (TV-01). */
@Composable
private fun Landing(
    topic: TopicState,
    list: LazyListState,
) {
    LaunchedEffect(topic.landing, topic.loading) {
        if (topic.loading) return@LaunchedEffect
        val key = if (topic.unread != null) "unread" else topic.landing
        val index = key?.let { indexOf(topic, it) } ?: lastIndex(topic)
        list.scrollToItem(index)
    }
}

/**
 * While the member scrolls: more of the window near its edges (TV-05), the read position
 * with the last message on screen (TV-06), the unread line fading after a while (TV-01).
 */
@Composable
private fun Following(
    topic: TopicState,
    list: LazyListState,
    actions: TopicActions,
) {
    val current by rememberUpdatedState(topic)
    val act by rememberUpdatedState(actions)
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } }
            .distinctUntilChanged()
            .collect { keys ->
                if ("earlier" in keys) act.onEarlier()
                if ("later" in keys) act.onLater()
                keys.lastOrNull { k -> current.messages.any { it.id == k } }?.let(act.onSeen)
                keys.firstOrNull { k -> current.messages.any { it.id == k } }?.let(act.onTop)
            }
    }
    LaunchedEffect(topic.unread?.messageId) {
        if (topic.unread == null) return@LaunchedEffect
        delay(UNREAD_LINE_MS)
        act.onUnreadShown()
    }
}

internal fun indexOf(
    topic: TopicState,
    key: String,
): Int {
    var index = 1 + (if (topic.opening != null) 1 else 0)
    if (topic.earlier > 0 || topic.loadingEarlier) index++
    topic.messages.forEach { message ->
        if (message.id == topic.unread?.messageId) {
            if (key == "unread") return index
            index++
        }
        if (message.id == key) return index
        index++
    }
    return 0
}

internal fun lastIndex(topic: TopicState): Int =
    (if (topic.opening != null) 1 else 0) + (if (topic.earlier > 0) 1 else 0) +
        topic.messages.size + (if (topic.unread != null) 1 else 0)

/** The red line fades after this long on screen (TV-01). */
private const val UNREAD_LINE_MS = 4_000L
