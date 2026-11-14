// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import org.idejo.app.core.data.TopicView
import org.idejo.app.core.designsystem.component.CategoryMark
import org.idejo.app.core.designsystem.component.CategoryMarkSize
import org.idejo.app.core.designsystem.component.TopicItem
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Category

/** A category's page (BR-02); the route's `id` names it. */
@Composable
fun CategoryRoute(
    onTopic: (String) -> Unit,
    onTag: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CategoryScreen(
        state = state,
        onTopic = onTopic,
        onTag = onTag,
        onRefresh = viewModel::refresh,
        onMore = viewModel::more,
        onView = viewModel::view,
        modifier = modifier,
    )
}

/**
 * The category's head (mark, name, description) with **View**, then its topics, pinned first
 * as the server orders them, read a page at a time as the reader scrolls (BR-02, BR-10).
 * **New topic** comes with the composer (M2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    state: CategoryUi,
    onTopic: (String) -> Unit,
    onTag: (String) -> Unit,
    onRefresh: () -> Unit,
    onMore: () -> Unit,
    onView: (TopicView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val topics = state.topics
    val list = rememberLazyListState()
    val picture = remember(state.origin) { pictures(state.origin) }
    val empty = stringResource(R.string.category_empty)
    EndlessScroll(list, onMore)
    PullToRefreshBox(topics.refreshing, onRefresh, modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "head") { Head(state.category, state.view, onView) }
            items(topics.items, key = { it.id }) { topic ->
                TopicItem(
                    topic = topic,
                    onClick = { onTopic(topic.id) },
                    expanded = state.view == TopicView.Expanded,
                    picture = picture,
                    onTag = { tag -> tag.slug?.let(onTag) },
                )
            }
            pagedEnd(topics, empty, onRefresh, onMore)
        }
    }
}

@Composable
private fun Head(
    category: Category?,
    view: TopicView,
    onView: (TopicView) -> Unit,
) {
    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (category != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryMark(
                    category.title,
                    IdejoTheme.colors.category(category.colour, MaterialTheme.colorScheme),
                    size = CategoryMarkSize.Card,
                )
                Text(
                    category.title,
                    Modifier.weight(1f).semantics { heading() },
                    style = IdejoTheme.typography.headlineSmall,
                )
            }
            category.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = IdejoTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(Modifier.align(Alignment.End)) { ViewToggle(view, onView) }
    }
}
