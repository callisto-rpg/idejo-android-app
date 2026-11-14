// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.markdown.IdejoMarkdown
import org.idejo.app.core.markdown.MarkdownBody
import org.idejo.app.core.model.Server

/** **Help** (BR-08). */
@Composable
fun HelpRoute(
    modifier: Modifier = Modifier,
    viewModel: HelpViewModel = hiltViewModel(),
) {
    val server by viewModel.server.collectAsStateWithLifecycle()
    HelpScreen(server, modifier)
}

/**
 * The keys that send, the signs that link or suggest, every `!` command of this forum and its
 * plugins (from [server]), hiding part of a message and, last, **Formatting text**: what to
 * type and how it shows, drawn as a message is.
 */
@Composable
fun HelpScreen(
    server: Server?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PageTitle(stringResource(R.string.help_title))
        Sending(brief = false)
        Signs()
        Commands(server)
        HelpSection(stringResource(R.string.help_spoilers)) {
            Entry(
                stringResource(R.string.help_spoiler_syntax),
                stringResource(R.string.help_spoiler_text),
                code = true,
            )
        }
        Formatting()
    }
}

/** **Sending**: Enter, a new line, and finishing a message; [brief] in Getting started. */
@Composable
internal fun Sending(brief: Boolean) {
    HelpSection(stringResource(R.string.help_sending)) {
        if (!brief) Paragraph(stringResource(R.string.help_sending_intro))
        Entry("Enter", stringResource(R.string.help_enter))
        if (!brief) {
            Entry(
                stringResource(R.string.help_new_line_keys),
                stringResource(R.string.help_new_line),
            )
        }
        Entry(
            stringResource(R.string.help_finish_keys),
            stringResource(if (brief) R.string.help_finish_brief else R.string.help_finish),
        )
    }
}

@Composable
private fun Signs() {
    HelpSection(stringResource(R.string.help_signs)) {
        Paragraph(stringResource(R.string.help_signs_intro))
        listOf(
            Triple("@", R.string.help_sign_user, R.string.help_sign_user_text),
            Triple("+", R.string.help_sign_role, R.string.help_sign_role_text),
            Triple("#", R.string.help_sign_topic, R.string.help_sign_topic_text),
            Triple("/", R.string.help_sign_category, R.string.help_sign_category_text),
            Triple("$", R.string.help_sign_tag, R.string.help_sign_tag_text),
            Triple("&", R.string.help_sign_forum, R.string.help_sign_forum_text),
            Triple("!", R.string.help_sign_command, R.string.help_sign_command_text),
        ).forEach { (sign, word, text) -> Entry(sign + stringResource(word), stringResource(text)) }
    }
}

/** The forum's commands and plugins; an older server without them shows the words only. */
@Composable
private fun Commands(server: Server?) {
    HelpSection(stringResource(R.string.help_commands)) {
        Paragraph(stringResource(R.string.help_commands_intro))
        server?.commands.orEmpty().forEach { command ->
            Entry("!" + command.name, command.help ?: command.detail.orEmpty(), code = true)
        }
        val plugins = server?.plugins.orEmpty()
        if (plugins.isNotEmpty()) {
            Text(
                stringResource(R.string.help_plugins),
                style = IdejoTheme.typography.titleMedium,
            )
            Paragraph(stringResource(R.string.help_plugins_intro))
            plugins.forEach { plugin ->
                Entry(
                    "!" + plugin.command,
                    plugin.help ?: plugin.description.orEmpty(),
                    code = true,
                )
            }
        }
    }
}

@Composable
private fun Formatting() {
    HelpSection(stringResource(R.string.help_formatting)) {
        Paragraph(stringResource(R.string.help_formatting_intro))
        formats().forEach { format ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Code(format.syntax)
                Text(format.label, style = IdejoTheme.typography.bodyMedium)
                if (format.shown) {
                    val document = remember(format.syntax) { IdejoMarkdown.parse(format.syntax) }
                    MarkdownBody(
                        document,
                        Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerLow,
                                RoundedCornerShape(10.dp),
                            ).padding(10.dp),
                    )
                }
            }
        }
    }
}

/** A section of Help, with its heading. */
@Composable
internal fun HelpSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title)
        content()
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text,
        style = IdejoTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A term (keys, a sign, a command) and what it does. */
@Composable
private fun Entry(
    term: String,
    text: String,
    code: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (code) {
            Code(
                term,
            )
        } else {
            Text(
                term,
                style = IdejoTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(text, style = IdejoTheme.typography.bodyMedium)
    }
}

@Composable
private fun Code(text: String) {
    Text(
        text,
        Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = IdejoTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
    )
}
