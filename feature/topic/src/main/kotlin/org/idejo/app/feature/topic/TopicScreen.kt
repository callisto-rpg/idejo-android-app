// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.idejo.app.core.data.TopicState
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Tag
import org.idejo.app.core.model.Topic

/** Where the topic page leads: a member's card, a tag's topics, a link. */
data class TopicActions(
    val onUser: (String) -> Unit = {},
    val onTag: (String) -> Unit = {},
    val onLink: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onEarlier: () -> Unit = {},
    val onLater: () -> Unit = {},
    val onEnd: () -> Unit = {},
    val onSeen: (String) -> Unit = {},
    val onUnreadShown: () -> Unit = {},
    val onFollow: (Boolean) -> Unit = {},
    val onMessage: (String) -> Unit = {},
    val onTopic: (String) -> Unit = {},
    val onVote: (String, String, List<String>) -> Unit = { _, _, _ -> },
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
    onTopic: (String, String?) -> Unit,
    viewModel: TopicViewModel = hiltViewModel(),
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
            onRetry = { viewModel.retry() },
            onEarlier = { viewModel.earlier() },
            onLater = { viewModel.later() },
            onEnd = { viewModel.end() },
            onSeen = { viewModel.seen(it) },
            onUnreadShown = { viewModel.unreadShown() },
            onFollow = { viewModel.follow(it) },
            onMessage = { id ->
                scope.launch {
                    viewModel.reach(id)?.takeIf { it != here }?.let { onTopic(it, id) }
                }
            },
            onTopic = { onTopic(it, null) },
            onVote = { message, poll, options -> viewModel.vote(message, poll, options) },
        ),
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
    list: LazyListState = rememberLazyListState(),
) {
    val topic = state.topic
    Box(modifier.fillMaxSize()) {
        when {
            topic.gone -> Gone()
            topic.topic == null && topic.loading -> Spinner()
            topic.topic == null -> topic.problem?.let { Problem(it, actions.onRetry) }
            else -> Messages(state, actions, list)
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
}

@Composable
private fun Messages(
    state: TopicUi,
    actions: TopicActions,
    list: LazyListState,
) {
    val topic = state.topic
    val open = topic.topic?.let { !it.locked && !it.archived } == true
    val context =
        MessageContext(
            state.origin,
            state.emoji,
            onUser = actions.onUser,
            onLink = actions.onLink,
            onMessage = actions.onMessage,
            onTopic = actions.onTopic,
            onVote = actions.onVote,
            mayVote = open && state.signedIn,
            voting = topic.voting,
        )
    LazyColumn(
        Modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item(key = "header") { Header(topic.topic!!, actions) }
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
            item(key = message.id) { MessageItem(message, context) }
        }
        if (topic.later > 0 || topic.loadingLater) {
            item(
                key = "later",
            ) { More(topic.later, earlier = false, topic.loadingLater, actions.onLater) }
        }
    }
    Landing(topic, list)
    Following(topic, list, actions)
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
            }
    }
    LaunchedEffect(topic.unread?.messageId) {
        if (topic.unread == null) return@LaunchedEffect
        delay(UNREAD_LINE_MS)
        act.onUnreadShown()
    }
}

private fun indexOf(
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

private fun lastIndex(topic: TopicState): Int =
    (if (topic.opening != null) 1 else 0) + (if (topic.earlier > 0) 1 else 0) +
        topic.messages.size + (if (topic.unread != null) 1 else 0)

/** The red line fades after this long on screen (TV-01). */
private const val UNREAD_LINE_MS = 4_000L
