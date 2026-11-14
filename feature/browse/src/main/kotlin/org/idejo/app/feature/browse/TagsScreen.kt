// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.idejo.app.core.data.TagGroup
import org.idejo.app.core.data.tagGroups
import org.idejo.app.core.designsystem.component.TopicItem
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Tag

/** **Filter by tag** (BR-04); the route's `tags` are ticked and shown at once. */
@Composable
fun TagsRoute(
    onTopic: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TagsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TagsScreen(
        state = state,
        actions =
            TagsActions(
                onTick = viewModel::tick,
                onOperator = viewModel::operator,
                onShow = viewModel::show,
                onTopic = onTopic,
                onRefresh = viewModel::refresh,
                onMore = viewModel::more,
            ),
        modifier = modifier,
    )
}

/** What the member does on **Filter by tag**. */
class TagsActions(
    val onTick: (String) -> Unit,
    val onOperator: (any: Boolean) -> Unit,
    val onShow: () -> Unit,
    val onTopic: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onMore: () -> Unit,
)

/**
 * The tags to tick, **Show topics** and **Operator** (And, Or), then the topics carrying all
 * or any of the tags shown, a page at a time (BR-04, BR-10).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    state: TagsUi,
    actions: TagsActions,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    val picture = remember(state.origin) { pictures(state.origin) }
    val topics = state.topics
    val noneAny = stringResource(R.string.tags_none_any)
    val noneAll = stringResource(R.string.tags_none_all)
    EndlessScroll(list, actions.onMore)
    PullToRefreshBox(topics.refreshing, actions.onRefresh, modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "form") { Form(state, actions) }
            val shown = state.shown ?: return@LazyColumn
            if (shown.ticked.isEmpty()) return@LazyColumn
            item(key = "heading") {
                SectionTitle(
                    stringResource(
                        when {
                            shown.ticked.size == 1 -> R.string.tags_one
                            shown.any -> R.string.tags_any
                            else -> R.string.tags_all
                        },
                    ),
                )
            }
            items(topics.items, key = { it.id }) { topic ->
                TopicItem(
                    topic = topic,
                    onClick = { actions.onTopic(topic.id) },
                    category = state.categories.find(topic.categoryId),
                    picture = picture,
                    onTag = { tag -> tag.slug?.let(actions.onTick) },
                )
            }
            val empty = if (shown.any) noneAny else noneAll
            pagedEnd(topics, empty, actions.onRefresh, actions.onMore)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Form(
    state: TagsUi,
    actions: TagsActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageTitle(stringResource(R.string.tags_title))
        Text(
            stringResource(R.string.tags_intro),
            style = IdejoTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            state.tagsLoading && state.tags.isEmpty() -> {
                MoreSpinner()
            }

            state.tagsProblem != null && state.tags.isEmpty() -> {
                ProblemRow(state.tagsProblem, actions.onRefresh)
            }

            state.tags.isEmpty() -> {
                Text(stringResource(R.string.tags_none), style = IdejoTheme.typography.bodyLarge)
            }

            else -> {
                val groups =
                    remember(
                        state.tags,
                        state.categories,
                    ) { tagGroups(state.tags, state.categories) }
                groups.forEach { group -> Group(group, state.choice.ticked, actions.onTick) }
                Actions(state.choice, actions)
            }
        }
    }
}

/** One category's tags under its name, or the tags of every category (BR-04, IX-10). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Group(
    group: TagGroup,
    ticked: Set<String>,
    onTick: (String) -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    if (group.titled) {
        Text(
            group.category?.title ?: stringResource(R.string.tags_every_category),
            Modifier.semantics { heading() },
            style = IdejoTheme.typography.titleSmall,
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        group.tags.forEach { tag -> TagChip(tag, ticked, onTick) }
    }
}

@Composable
private fun TagChip(
    tag: Tag,
    ticked: Set<String>,
    onTick: (String) -> Unit,
) {
    val slug = tag.slug ?: return
    FilterChip(
        selected = slug in ticked,
        onClick = { onTick(slug) },
        label = { Text(tag.name) },
    )
}

/** **Show topics** first, then **Operator**, as on the web. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(
    choice: TagChoice,
    actions: TagsActions,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = actions.onShow, enabled = choice.ticked.isNotEmpty()) {
            Text(stringResource(R.string.tags_show))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.tags_operator),
                style = IdejoTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow {
                listOf(false to R.string.tags_and, true to R.string.tags_or).forEachIndexed {
                    index,
                    (any, label),
                    ->
                    SegmentedButton(
                        selected = choice.any == any,
                        onClick = { actions.onOperator(any) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        icon = {},
                    ) { Text(stringResource(label), style = IdejoTheme.typography.labelMedium) }
                }
            }
        }
    }
}
