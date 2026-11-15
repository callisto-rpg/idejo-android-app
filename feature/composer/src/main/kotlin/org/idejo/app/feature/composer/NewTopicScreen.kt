// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.idejo.app.core.designsystem.component.CategoryMark
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.markdown.IdejoMarkdown
import org.idejo.app.core.markdown.MarkdownBody

/** **New topic** (CO-19); [onOpen] opens the topic started, [onDone] goes back. */
@Composable
fun NewTopicRoute(
    onOpen: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewTopicViewModel = hiltViewModel(),
) {
    val newTopic = viewModel.newTopic ?: return
    val form by newTopic.form.collectAsStateWithLifecycle()
    val open by rememberUpdatedState(onOpen)
    LaunchedEffect(form.created) { form.created?.let(open) }
    NewTopicScreen(form, viewModel.origin, newTopic::change, newTopic::create, onDone, modifier)
}

/**
 * Title, category, tags (at least one where the category has any), the icon of a topical
 * topic, **topical** for a moderator, and the first message with **Preview**, never live.
 * Waiting for approval, it says so.
 */
@Composable
fun NewTopicScreen(
    form: NewTopicForm,
    origin: String,
    onChange: ((NewTopicForm) -> NewTopicForm) -> Unit,
    onCreate: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier.verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    Text(
        stringResource(R.string.new_topic),
        Modifier.semantics { heading() },
        style = IdejoTheme.typography.headlineSmall,
    )
    val pending = form.pending
    if (pending != null) {
        Text(stringResource(R.string.new_topic_pending, pending))
        Button(onClick = onDone) { Text(stringResource(R.string.new_topic_ok)) }
        return@Column
    }
    OutlinedTextField(
        form.title,
        { title -> onChange { it.copy(title = title) } },
        Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.new_topic_title)) },
        singleLine = true,
    )
    CategoryChoice(form, onChange)
    Tags(form, onChange)
    if (form.mayMakeTopical) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(form.topical, { topical -> onChange { it.copy(topical = topical) } })
            Text(stringResource(R.string.new_topic_topical))
        }
    }
    if (form.isTopical) Icons(form, origin, onChange)
    FirstMessage(form, onChange)
    form.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onCreate, enabled = form.ready) {
            Text(stringResource(R.string.new_topic_create))
        }
        TextButton(onClick = onDone) { Text(stringResource(R.string.command_cancel)) }
    }
}

@Composable
private fun CategoryChoice(
    form: NewTopicForm,
    onChange: ((NewTopicForm) -> NewTopicForm) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            val category = form.category
            if (category != null) {
                CategoryMark(category, Modifier.padding(end = 8.dp))
                Text(category.title)
            } else {
                Text(stringResource(R.string.new_topic_category))
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            form.categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.title) },
                    leadingIcon = { CategoryMark(category) },
                    onClick = {
                        open = false
                        onChange { it.copy(categoryId = category.id) }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tags(
    form: NewTopicForm,
    onChange: ((NewTopicForm) -> NewTopicForm) -> Unit,
) {
    val tags = form.category?.tags.orEmpty()
    if (tags.isEmpty()) return
    Column {
        Text(stringResource(R.string.new_topic_tags), style = IdejoTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                val on = tag.id in form.tagIds
                FilterChip(
                    on,
                    {
                        onChange {
                            it.copy(
                                tagIds = if (on) it.tagIds - tag.id else it.tagIds + tag.id,
                            )
                        }
                    },
                    { Text(tag.name) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Icons(
    form: NewTopicForm,
    origin: String,
    onChange: ((NewTopicForm) -> NewTopicForm) -> Unit,
) {
    if (form.icons.isEmpty()) return
    Column {
        Text(stringResource(R.string.new_topic_icon), style = IdejoTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            form.icons.forEach { icon ->
                FilterChip(
                    icon.id == form.iconId,
                    { onChange { it.copy(iconId = icon.id) } },
                    { Text(icon.name) },
                    leadingIcon =
                        icon.url?.let { url ->
                            { AsyncImage(absolute(origin, url), null, Modifier.size(20.dp)) }
                        },
                )
            }
        }
    }
}

@Composable
private fun FirstMessage(
    form: NewTopicForm,
    onChange: ((NewTopicForm) -> NewTopicForm) -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row {
        FilterChip(
            !form.preview,
            { onChange { it.copy(preview = false) } },
            { Text(stringResource(R.string.new_topic_write)) },
            Modifier.padding(end = 8.dp),
        )
        FilterChip(
            form.preview,
            { onChange { it.copy(preview = true) } },
            { Text(stringResource(R.string.new_topic_preview)) },
        )
    }
    if (form.preview) {
        MarkdownBody(remember(form.body) { IdejoMarkdown.parse(form.body) })
    } else {
        OutlinedTextField(
            form.body,
            { body -> onChange { it.copy(body = body) } },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.new_topic_message)) },
            minLines = MESSAGE_LINES,
        )
    }
}

private const val MESSAGE_LINES = 6
