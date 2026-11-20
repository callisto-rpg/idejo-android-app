// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.idejo.app.core.designsystem.component.TopicItem

/** **Unread** (BR-06). */
@Composable
fun UnreadRoute(
    onTopic: (String) -> Unit,
    onTag: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Whether the page shows its title; the home page's tab does not. */
    titled: Boolean = true,
    viewModel: UnreadViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UnreadScreen(state, onTopic, onTag, viewModel::refresh, viewModel::more, modifier, titled)
}

/**
 * Every followed topic with news under **Following**, then the **Other topics** a page at a
 * time, each with its category; the list follows `unread.changed` by itself (BR-06).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnreadScreen(
    state: UnreadUi,
    onTopic: (String) -> Unit,
    onTag: (String) -> Unit,
    onRefresh: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    titled: Boolean = true,
) {
    val unread = state.unread
    val others = unread.others
    val list = rememberLazyListState()
    val picture = remember(state.origin) { pictures(state.origin) }
    val nothing = stringResource(R.string.unread_nothing)
    EndlessScroll(list, onMore)
    PullToRefreshBox(others.refreshing, onRefresh, modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (titled) item(key = "title") { PageTitle(stringResource(R.string.unread_title)) }
            if (unread.following.isNotEmpty()) {
                item(key = "following") { SectionTitle(stringResource(R.string.unread_following)) }
                items(unread.following, key = { "f:${it.id}" }) { topic ->
                    TopicItem(
                        topic = topic,
                        onClick = { onTopic(topic.id) },
                        category = state.categories.find(topic.categoryId),
                        picture = picture,
                        onTag = { tag -> tag.slug?.let(onTag) },
                    )
                }
                if (others.items.isNotEmpty()) {
                    item(key = "others") { SectionTitle(stringResource(R.string.unread_others)) }
                }
            }
            items(others.items, key = { "o:${it.id}" }) { topic ->
                TopicItem(
                    topic = topic,
                    onClick = { onTopic(topic.id) },
                    category = state.categories.find(topic.categoryId),
                    picture = picture,
                    onTag = { tag -> tag.slug?.let(onTag) },
                )
            }
            pagedEnd(others, nothing.takeIf { unread.empty }, onRefresh, onMore)
        }
    }
}
