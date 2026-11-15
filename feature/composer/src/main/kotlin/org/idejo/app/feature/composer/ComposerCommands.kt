// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.idejo.app.core.api.Gif
import org.idejo.app.core.api.GifPage
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Command
import org.idejo.app.core.model.GifConfig
import org.idejo.app.core.model.PollCreate
import org.idejo.app.core.model.Scheduled
import org.idejo.app.core.model.Suggestion
import org.idejo.app.core.model.Upload
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the `!` commands do (CO-13), images (CO-14) and scheduled messages (CO-20). */
data class CommandEvents(
    val commands: suspend () -> List<Command> = { emptyList() },
    val onSchedule: (Double) -> Unit = {},
    val onPoll: (PollCreate) -> Unit = {},
    /** `!spoiler`, with `!spoiler:overlay:`'s words. */
    val onSpoiler: (String?) -> Unit = {},
    val onPlugin: (String, Map<String, String>) -> Unit = { _, _ -> },
    val onAutomessage: (String) -> Unit = {},
    /** `!forward`: the card for the message replied to. */
    val onForward: (String) -> Unit = {},
    val upload: suspend (ImageFile) -> Upload? = { null },
    /** What the forum takes: pictures are made to fit it (CO-14). */
    val limits: suspend () -> UploadLimits = { UploadLimits() },
    val onImageRefused: () -> Unit = {},
    /** Pictures shared to the app for this topic (CO-14), taken once. */
    val takeShared: () -> List<ImageFile> = { emptyList() },
    val onCancelScheduled: (String) -> Unit = {},
    /** GIFs' provider, null while they are off (CO-15). */
    val gifConfig: suspend () -> GifConfig? = { null },
    val gifPage: suspend (String?, Int) -> GifPage? = { _, _ -> null },
    val onGifPicked: (Gif, String?) -> Unit = { _, _ -> },
    /** `!new-topic`, with `!new-topic:Title:`'s title (CO-19). */
    val onNewTopic: (String?) -> Unit = {},
)

/** A command's dialog open over the box. */
internal sealed interface Asking {
    data object Schedule : Asking

    data object Poll : Asking

    data class Plugin(
        val command: Command,
        val first: String?,
    ) : Asking

    data class AltText(
        val markdown: String,
    ) : Asking

    data class Gifs(
        val config: GifConfig,
    ) : Asking
}

/** The commands run from the box: `!` typed, picked, or the toolbar's **+** (CO-13). */
@Stable
internal class Commanding(
    private val events: CommandEvents,
    val value: () -> TextFieldValue,
    val change: (TextFieldValue) -> Unit,
    private val replying: () -> String?,
) {
    var asking by mutableStateOf<Asking?>(null)
    var list by mutableStateOf(emptyList<Command>())

    /** The GIF provider, once known to be on (CO-15). */
    var gifs by mutableStateOf<GifConfig?>(null)

    /** Opens the photo picker; the picture goes at the end or at the caret. */
    var pickImage: (atEnd: Boolean) -> Unit = {}

    /** The camera, the clipboard, drops and the share sheet (CO-14). */
    var images: ImageSources? = null

    fun named(name: String) = list.firstOrNull { it.name == name }

    /** A suggestion taken: a command runs, its `!` gone; anything else goes in. */
    fun picked(
        at: SuggestAt,
        item: Suggestion,
    ) {
        if (at.sign == '!') {
            change(value().without(at.start))
            named(item.insert)?.let { run(it) }
        } else {
            change(value().accept(at, item.insert))
        }
    }

    /** What was typed: `!spoiler:words:` and `!plugin:argument:` run at the closing colon. */
    fun typed(next: TextFieldValue) {
        val found = next.typedCommand()
        val command = found?.let { named(it.name) }?.takeIf(::runsTyped)
        if (found != null && command != null) {
            change(next.without(found.start))
            run(command, found.argument)
        } else {
            change(next)
        }
    }

    fun run(
        command: Command,
        argument: String? = null,
    ) {
        when (command.name) {
            "image" -> pickImage(true)
            "gif" -> gifs?.let { asking = Asking.Gifs(it) }
            "schedule" -> asking = Asking.Schedule
            "poll" -> asking = Asking.Poll
            "spoiler" -> events.onSpoiler(argument)
            "forward" -> replying()?.let(events.onForward)
            "new-topic" -> events.onNewTopic(argument)
            else -> runPlugin(command, argument)
        }
    }

    /** A plugin or an automessage of the forum. */
    private fun runPlugin(
        command: Command,
        argument: String?,
    ) {
        when {
            command.block == "collapse" -> {
                change(value().atLineStart("!${command.name} "))
            }

            command.kind == "automessage" -> {
                events.onAutomessage(command.name)
            }

            command.kind == "plugin" && argument != null && command.block == "video" -> {
                events.onPlugin(command.name, mapOf("link" to argument))
            }

            command.kind == "plugin" -> {
                asking = Asking.Plugin(command, argument)
            }
        }
    }

    /** A GIF chosen: its Markdown at the caret, then the provider told. */
    fun gifPicked(
        gif: Gif,
        query: String?,
        label: String,
    ) {
        asking = null
        change(value().insert(gifMarkdown(gif, label)))
        events.onGifPicked(gif, query)
    }

    /** An uploaded picture's Markdown in the box, then its description asked. */
    fun uploaded(
        upload: Upload,
        atEnd: Boolean,
    ) {
        val box = value()
        change(if (atEnd) box.appendLine(upload.markdown) else box.insert(upload.markdown))
        asking = Asking.AltText(upload.markdown)
    }

    fun described(
        markdown: String,
        alt: String,
    ) {
        asking = null
        val box = value()
        val at = box.text.indexOf(markdown)
        if (alt.isEmpty() || at < 0) return
        val next = box.text.replaceRange(at, at + markdown.length, withAlt(markdown, alt))
        change(TextFieldValue(next, TextRange(next.length - (box.text.length - box.selection.end))))
    }

    /** The commands of the toolbar's **+**, as the web's. */
    val more: List<Command>
        get() = list.filter { it.name in MORE || it.kind == "plugin" }
}

@Composable
internal fun rememberCommanding(
    events: CommandEvents,
    value: () -> TextFieldValue,
    change: (TextFieldValue) -> Unit,
    replying: () -> String?,
): Commanding {
    val commanding = remember { Commanding(events, value, change, replying) }
    val source by rememberUpdatedState(events.commands)
    val gifSource by rememberUpdatedState(events.gifConfig)
    LaunchedEffect(Unit) {
        val gifs = gifSource()
        commanding.gifs = gifs
        commanding.list = source().filter { it.name != "gif" || gifs != null }
    }
    val images = rememberImageSources(commanding, events)
    commanding.images = images
    commanding.pickImage = images.pick
    return commanding
}

/** Commands that run at the closing colon of `!command:argument:`. */
private fun runsTyped(command: Command) =
    command.name == "spoiler" || command.name == "new-topic" || command.kind == "plugin"

/** The open dialog, if any. */
@Composable
internal fun CommandDialogs(
    commanding: Commanding,
    events: CommandEvents,
    emoji: EmojiSource,
) {
    val close = { commanding.asking = null }
    when (val asking = commanding.asking) {
        null -> {}

        Asking.Schedule -> {
            ScheduleDialog({ hours ->
                close()
                events.onSchedule(hours)
            }, close)
        }

        Asking.Poll -> {
            PollDialog(emoji, { poll ->
                close()
                events.onPoll(poll)
            }, close)
        }

        is Asking.Plugin -> {
            PluginDialog(asking.command, asking.first, { params ->
                close()
                events.onPlugin(asking.command.name, params)
            }, close)
        }

        is Asking.AltText -> {
            AltTextDialog({ commanding.described(asking.markdown, it) }, close)
        }

        is Asking.Gifs -> {
            val label = stringResource(R.string.composer_gif)
            GifPicker(
                asking.config,
                events.gifPage,
                { gif, query -> commanding.gifPicked(gif, query, label) },
                close,
            )
        }
    }
}

/** The member's scheduled messages under the box, each with **Cancel** (CO-20). */
@Composable
internal fun ScheduledList(
    items: List<Scheduled>,
    onCancel: (String) -> Unit,
) = items.forEach { item ->
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.scheduled_at, WHEN.format(item.postAt)) + " " +
                item.bodyMd.orEmpty().take(EXCERPT),
            Modifier.weight(1f),
            style = IdejoTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
        TextButton(
            onClick = { onCancel(item.id) },
        ) { Text(stringResource(R.string.scheduled_cancel)) }
    }
}

/** `!command:argument:` just typed before the caret (CO-13), run at its closing colon. */
internal data class TypedCommand(
    val name: String,
    val argument: String,
    val start: Int,
)

internal fun TextFieldValue.typedCommand(): TypedCommand? {
    if (!selection.collapsed) return null
    val found = TYPED.find(text.substring(0, selection.start)) ?: return null
    val start = found.groups[1]!!.range.first - 1
    return TypedCommand(found.groupValues[1], found.groupValues[2], start)
}

/** The box without what was typed from [start] to the caret. */
internal fun TextFieldValue.without(start: Int): TextFieldValue =
    TextFieldValue(text.removeRange(start, selection.start), TextRange(start))

/** [line] put at the start of the caret's line (`!collapse`). */
internal fun TextFieldValue.atLineStart(line: String): TextFieldValue {
    val from = text.lastIndexOf('\n', selection.min - 1) + 1
    return TextFieldValue(
        text.replaceRange(from, from, line),
        TextRange(selection.min + line.length),
    )
}

/** [markdown] at the end of the message, on a line of its own (`!image`). */
internal fun TextFieldValue.appendLine(markdown: String): TextFieldValue {
    val base = text.trimEnd()
    val next = if (base.isEmpty()) markdown else "$base\n$markdown"
    return TextFieldValue(next, TextRange(next.length))
}

private val TYPED = Regex("""(?:^|\s)!([a-z0-9-]+):([^:\n]+):$""")
private val MORE = setOf("spoiler", "poll", "schedule", "forward", "new-topic")
private const val EXCERPT = 60
private val WHEN =
    DateTimeFormatter
        .ofLocalizedDateTime(
            FormatStyle.MEDIUM,
            FormatStyle.SHORT,
        ).withZone(ZoneId.systemDefault())
