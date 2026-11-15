// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.idejo.app.core.data.TutorialBubbles
import org.idejo.app.core.designsystem.component.Avatar
import org.idejo.app.core.designsystem.component.StatusBubble
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.designsystem.theme.parseHexColor
import org.idejo.app.core.designsystem.theme.pictureUrl
import org.idejo.app.core.model.ApiError
import org.idejo.app.core.model.Emoji
import org.idejo.app.core.model.Me
import org.idejo.app.core.model.Scheduled
import org.idejo.app.core.model.Suggestion

/** What the member does in the composer. */
data class ComposerEvents(
    /** The box's text, after a soft return was taken out (CO-03). */
    val onTyped: (String) -> Unit = {},
    val onEnter: () -> Unit = {},
    val onComplete: () -> Unit = {},
    val onErrorSeen: () -> Unit = {},
    val onCancelReply: () -> Unit = {},
    val onCancelForward: () -> Unit = {},
    /** **Append to my message** after `too_soon` (CO-16). */
    val onAppendToOpen: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    /** The suggestions after a sign (CO-12). */
    val suggest: suspend (Char, String) -> List<Suggestion> = { _, _ -> emptyList() },
    /** The emoji picker's search (CO-10). */
    val emoji: suspend (String) -> List<Emoji> = { emptyList() },
    /** The `!` commands, images and scheduled messages (CO-13, CO-14, CO-20). */
    val commands: CommandEvents = CommandEvents(),
)

/** The composer driven by [composer]: what the app places in the topic (CO-01). */
@Composable
fun ComposerRoute(
    composer: Composer,
    author: Me?,
    origin: String,
    bubbles: TutorialBubbles,
    modifier: Modifier = Modifier,
    onForward: (String) -> Unit = {},
) {
    val state by composer.state.collectAsStateWithLifecycle()
    val scheduled by composer.scheduled.collectAsStateWithLifecycle()
    LaunchedEffect(composer) { composer.refreshScheduled() }
    ComposerBox(
        state,
        author,
        origin,
        ComposerEvents(
            onTyped = composer::typed,
            onEnter = composer::enter,
            onComplete = composer::complete,
            onErrorSeen = composer::errorSeen,
            onCancelReply = composer::cancelReply,
            onCancelForward = composer::cancelForward,
            onAppendToOpen = composer::appendToOpen,
            onConfirmDelete = composer::confirmDelete,
            onCancelDelete = composer::cancelDelete,
            suggest = composer::suggestions,
            emoji = composer::emoji,
            commands =
                CommandEvents(
                    commands = composer::commands,
                    onSchedule = composer::schedule,
                    onPoll = composer::poll,
                    onSpoiler = composer::spoiler,
                    onPlugin = composer::plugin,
                    onAutomessage = composer::automessage,
                    onForward = onForward,
                    upload = composer::upload,
                    onCancelScheduled = composer::cancelScheduled,
                ),
        ),
        modifier,
        bubbles,
        graphical = author?.graphicalComposer != false,
        scheduled = scheduled,
    )
}

/**
 * The author's open message, in place (CO-01): the head with the picture, name and status
 * bubble, faded until something is sent (CO-05); the text, sent in higher contrast than
 * unsent, what was just sent glowing (CO-04); the server's errors under it (CO-08).
 */
@Composable
fun ComposerBox(
    state: ComposerState,
    author: Me?,
    origin: String,
    events: ComposerEvents,
    modifier: Modifier = Modifier,
    bubbles: TutorialBubbles = TutorialBubbles.All,
    graphical: Boolean = true,
    scheduled: List<Scheduled> = emptyList(),
) {
    var value by rememberFollowing(state.text)
    val change = { next: TextFieldValue ->
        value = next
        events.onTyped(next.text)
    }
    val commanding = rememberCommanding(events.commands, { value }, change, { state.replying?.id })
    val suggesting =
        rememberSuggesting(value) { sign, query ->
            when (sign) {
                '!' -> commandSuggestions(commanding.list, query)
                else -> events.suggest(sign, query)
            }
        }
    val pick: (Suggestion) -> Unit = { item -> suggesting.at?.let { commanding.picked(it, item) } }
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Head(state.status, author, origin)
        state.replying?.let { ReplyingBox(it, events.onCancelReply) }
        state.forwarding?.let { ForwardBox(it, state.sent.isEmpty(), events.onCancelForward) }
        Editor(state, value, events, graphical, suggesting, pick, commanding::typed)
        SuggestionList(suggesting) { pick(it) }
        ScheduledList(scheduled, events.commands.onCancelScheduled)
        CommandDialogs(commanding, events.commands)
        Tip(state, bubbles)
        state.line?.let { SlowModeLine(it, bubbles) }
        state.error?.let { error ->
            val append =
                state.draftOver != null &&
                    (error as? ComposerError.Refused)?.error?.code == ApiError.TOO_SOON
            ErrorLine(error, events.onErrorSeen, if (append) events.onAppendToOpen else null)
        }
        if (state.confirmDelete) DeleteAsk(events)
        if (graphical) GraphicalTools(state, origin, events, commanding)
    }
}

/** The graphical composer's row and its emoji picker (CO-10). */
@Composable
private fun GraphicalTools(
    state: ComposerState,
    origin: String,
    events: ComposerEvents,
    commanding: Commanding,
) {
    var picking by remember { mutableStateOf(false) }
    Toolbar(
        Tools(commanding.value, commanding.change, events, { picking = true }, commanding),
        editing = state.editing != null,
        waiting = { state.line?.done(System.currentTimeMillis()) == false },
    )
    if (picking) {
        EmojiPicker(
            origin,
            events.emoji,
            onPick = {
                picking = false
                commanding.change(commanding.value().insert(":${it.name}:"))
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun Head(
    status: ComposerStatus,
    author: Me?,
    origin: String,
) {
    val faded = status == ComposerStatus.Empty || status == ComposerStatus.Unsent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val name = author?.displayName.orEmpty()
        val picture = pictureUrl(author?.avatarUrl, author?.avatarDarkUrl, IdejoTheme.dark)
        Row(
            Modifier.alpha(if (faded) FADED else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Avatar(
                name,
                parseHexColor(author?.colour) ?: MaterialTheme.colorScheme.tertiary,
                image =
                    picture?.let { url ->
                        {
                            AsyncImage(
                                absolute(origin, url),
                                null,
                                contentScale = ContentScale.Crop,
                            )
                        }
                    },
            )
            Text(name, style = IdejoTheme.typography.titleSmall)
        }
        statusText(status)?.let { StatusBubble(it) }
    }
}

@Composable
private fun statusText(status: ComposerStatus): String? =
    when (status) {
        ComposerStatus.Empty -> null
        ComposerStatus.Unsent -> stringResource(R.string.composer_unsent)
        ComposerStatus.Sent -> stringResource(R.string.composer_sent)
        ComposerStatus.SentAppendsUnsent -> stringResource(R.string.composer_appends)
        ComposerStatus.SentAndCommitted -> stringResource(R.string.composer_committed)
        ComposerStatus.Editing -> null
    }

@Composable
private fun Editor(
    state: ComposerState,
    value: TextFieldValue,
    events: ComposerEvents,
    graphical: Boolean,
    suggesting: Suggesting,
    pick: (Suggestion) -> Unit,
    onChange: (TextFieldValue) -> Unit,
) {
    val colors = textColors(rememberGlow(state.justSent))
    val label = stringResource(R.string.composer_label)
    Box {
        if (value.text.isEmpty()) {
            Text(
                stringResource(R.string.composer_placeholder),
                style = IdejoTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = { next ->
                when {
                    softReturn(value.text, next.text) != null -> events.onEnter()
                    else -> onChange(bulletRemoved(value, next) ?: next)
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = label }
                    .onPreviewKeyEvent { key ->
                        suggesting.key(key) { pick(it) } ||
                            keys(key, events) { onChange(value.newLine()) }
                    },
            textStyle = IdejoTheme.typography.bodyLarge.copy(color = colors.sent),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation =
                SentText(
                    state,
                    colors,
                    graphical,
                    stringResource(R.string.composer_image_pill),
                ),
        )
    }
}

/**
 * The tutorial bubble at the end of the text (CO-06): **Enter to send**, then **Sent!** with
 * "Message now visible to all!" for 2 s, then "Continue typing to add to your message".
 */
@Composable
private fun Tip(
    state: ComposerState,
    bubbles: TutorialBubbles,
) {
    var justSent by remember { mutableStateOf(false) }
    LaunchedEffect(state.sent) {
        justSent = state.sent.isNotEmpty()
        delay(VISIBLE_MILLIS)
        justSent = false
    }
    val tip = tutorialTip(state, justSent, bubbles) ?: return
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tip == TutorialTip.Sent && bubbles == TutorialBubbles.All) {
            Hint(stringResource(R.string.composer_tip_visible))
        }
        if (tip == TutorialTip.Continue) {
            Hint(stringResource(R.string.composer_tip_continue))
        } else {
            val text =
                if (tip ==
                    TutorialTip.Sent
                ) {
                    R.string.composer_tip_sent
                } else {
                    R.string.composer_tip_send
                }
            StatusBubble(stringResource(text))
        }
    }
}

@Composable
private fun Hint(text: String) =
    Text(
        text,
        style = IdejoTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

@Composable
private fun ErrorLine(
    error: ComposerError,
    onDismiss: () -> Unit,
    onAppend: (() -> Unit)?,
) {
    val text =
        when (error) {
            is ComposerError.Refused -> {
                val seconds = error.retryAfter?.seconds?.toInt()
                listOfNotNull(
                    error.error.message.ifBlank { null },
                    seconds?.let { pluralStringResource(R.plurals.composer_retry_after, it, it) },
                ).joinToString(" ")
            }

            ComposerError.Unreachable -> {
                stringResource(R.string.composer_unreachable)
            }

            ComposerError.NothingToHide -> {
                stringResource(R.string.composer_nothing_to_hide)
            }
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            Modifier.weight(1f),
            style = IdejoTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        if (onAppend != null) {
            TextButton(onClick = onAppend) { Text(stringResource(R.string.composer_append)) }
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.composer_error_dismiss)) }
    }
}

/** [url] as an absolute address: a path is the forum's. */
internal fun absolute(
    origin: String,
    url: String,
): String = if (url.startsWith("/")) origin.trimEnd('/') + url else url

private const val FADED = 0.6f
private const val VISIBLE_MILLIS = 2_000L
