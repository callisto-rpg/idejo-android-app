// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.idejo.app.core.model.Emoji
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.MessageState
import org.idejo.app.core.model.Reactor

/**
 * What the member may do with a message (MA-01 to MA-05): each null when not allowed here.
 * Copy text, Copy link and Share need nothing more than the message.
 */
@Immutable
data class MessageActions(
    /** The ten quick reactions (`default_reactions`), and the forum's emoji searched (MA-03). */
    val quick: List<Emoji> = emptyList(),
    val searchEmoji: suspend (String) -> List<Emoji> = { emptyList() },
    /** A reaction by its emoji's name, added (true) or taken back. */
    val onReact: ((String, String, Boolean) -> Unit)? = null,
    val reactors: suspend (String) -> List<Reactor> = { emptyList() },
    val onReply: ((Message) -> Unit)? = null,
    val onForward: ((Message) -> Unit)? = null,
    val onNewTopic: ((Message) -> Unit)? = null,
    /** Editing the member's own closed message (CO-18). */
    val onEdit: ((Message) -> Unit)? = null,
    /** A report with its reason; true once the moderators have it (MA-04). */
    val onReport: (suspend (String, String) -> Boolean)? = null,
    /** The member's id: their own messages are edited, never reported. */
    val me: String? = null,
    /** **Show message controls**: the actions under every message, not only a tapped one. */
    val always: Boolean = false,
)

/** One action of the bubble. */
private data class Action(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    val run: () -> Unit,
)

/**
 * The actions under a message (MA-01): React, Reply, Forward, New Topic, Edit, Report, Copy
 * text, Copy link, Share. When they do not fit, Reply, Forward and New Topic go under
 * **Quote**, as the web's (MA-02).
 */
@Composable
internal fun ActionsBar(
    message: Message,
    actions: MessageActions,
    origin: String,
    modifier: Modifier = Modifier,
) {
    if (message.state == MessageState.Deleted) return
    val android = LocalContext.current
    var reacting by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    val quote = quoteActions(message, actions)
    val after = laterActions(message, actions, android) { reporting = true }
    val react =
        actions.onReact?.let {
            Action(R.string.action_react, R.drawable.ic_add_reaction) {
                reacting =
                    true
            }
        }
    Surface(
        modifier.padding(top = 2.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        BoxWithConstraints {
            val count = (if (react != null) 1 else 0) + quote.size + after.size
            val grouped = quote.size > 1 && maxWidth < ACTION_WIDTH * count
            Row {
                react?.let {
                    Box {
                        ActionButton(it)
                        ReactionMenu(reacting, message, actions, origin) { reacting = false }
                    }
                }
                if (grouped) QuoteMenu(quote) else quote.forEach { ActionButton(it) }
                after.forEach { ActionButton(it) }
            }
        }
    }
    if (reporting) {
        actions.onReport?.let { report ->
            ReportDialog({ reason -> report(message.id, reason) }) { reporting = false }
        }
    }
}

@Composable
private fun ActionButton(action: Action) =
    IconButton(onClick = action.run) {
        Icon(painterResource(action.icon), stringResource(action.label))
    }

/** **Quote**: Reply, Forward, New Topic, when the bar is too narrow for them (MA-02). */
@Composable
private fun QuoteMenu(quote: List<Action>) =
    Box {
        var open by remember { mutableStateOf(false) }
        ActionButton(Action(R.string.action_quote, R.drawable.ic_format_quote) { open = true })
        DropdownMenu(open, onDismissRequest = { open = false }) {
            quote.forEach { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(action.label)) },
                    leadingIcon = { Icon(painterResource(action.icon), null) },
                    onClick = {
                        open = false
                        action.run()
                    },
                )
            }
        }
    }

private fun Context.copyText(text: String) {
    getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(getString(R.string.action_copy_text), text))
}

private fun Context.shareLink(url: String) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
    startActivity(Intent.createChooser(send, getString(R.string.action_share)))
}

/** Reply, Forward and New Topic: what **Quote** groups (MA-02). */
private fun quoteActions(
    message: Message,
    actions: MessageActions,
): List<Action> =
    listOfNotNull(
        actions.onReply?.let { Action(R.string.action_reply, R.drawable.ic_reply) { it(message) } },
        actions.onForward?.let {
            Action(
                R.string.action_forward,
                R.drawable.ic_forward,
            ) { it(message) }
        },
        actions.onNewTopic?.let {
            Action(R.string.action_new_topic, R.drawable.ic_add_comment) { it(message) }
        },
    )

/** Edit (the member's own), Report (others'), Copy text, Copy link, Share. */
private fun laterActions(
    message: Message,
    actions: MessageActions,
    android: Context,
    onReport: () -> Unit,
): List<Action> {
    val mine = message.author.id != null && message.author.id == actions.me
    val url = message.url
    val body = message.bodyMd?.takeIf { it.isNotBlank() }
    return listOfNotNull(
        actions.onEdit?.takeIf { mine && message.state == MessageState.Closed }?.let {
            Action(R.string.action_edit, R.drawable.ic_edit) { it(message) }
        },
        actions.onReport
            ?.takeIf {
                !mine
            }?.let { Action(R.string.action_report, R.drawable.ic_flag, onReport) },
        body?.let {
            Action(R.string.action_copy_text, R.drawable.ic_content_copy) { android.copyText(it) }
        },
        url?.let {
            Action(
                R.string.message_copy_link,
                R.drawable.ic_link,
            ) { android.copyLink(it) }
        },
        url?.let { Action(R.string.action_share, R.drawable.ic_share) { android.shareLink(it) } },
    )
}

/** An action's room: 48 dp. */
private val ACTION_WIDTH = 48.dp

/**
 * What the member may do with each message (MA-01): reacting and reporting once signed in;
 * replying where they may write; each action closes the bubble.
 */
@Composable
internal fun messageActions(
    state: TopicUi,
    actions: TopicActions,
    writing: TopicWriting,
    open: Boolean,
    close: () -> Unit,
): MessageActions {
    val topic = state.topic
    val signedIn = state.signedIn
    val wide =
        LocalWindowInfo.current.containerSize.width >=
            with(LocalDensity.current) { WIDE.roundToPx() }
    val then = { act: (Message) -> Unit ->
        { message: Message ->
            close()
            act(message)
        }
    }
    return MessageActions(
        quick = state.quick,
        searchEmoji = actions.searchEmoji,
        // Not queued offline (OF-05): reactions wait for the forum.
        onReact = actions.onReact.takeIf { signedIn && !state.offline },
        reactors = actions.reactors,
        onReply =
            then { writing.onReply(it, aboveOpen(topic, it, writing.openMessageId)) }
                .takeIf { signedIn && writing.mayWrite && open },
        onForward = then(writing.onForward).takeIf { signedIn },
        onNewTopic =
            then { writing.onNewTopic(it, topic.topic?.categoryId) }
                .takeIf { signedIn && topic.topic?.private != true },
        onEdit = then(writing.onEdit).takeIf { signedIn && writing.mayWrite },
        onReport = actions.onReport.takeIf { signedIn },
        me = state.me?.id,
        always = state.me?.let { if (wide) it.controlsOnDesktop else it.controlsOnMobile } == true,
    )
}

/** The message whose actions are open (MA-01): scrolling closes them, as Back does. */
@Composable
internal fun rememberActionsOpen(list: LazyListState): MutableState<String?> {
    val open = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress }.collect { if (it) open.value = null }
    }
    BackHandler(open.value != null) { open.value = null }
    return open
}

/** From this width, the desktop's choice of message controls applies. */
private val WIDE = 840.dp
