// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.idejo.app.core.data.TopicView
import org.idejo.app.core.designsystem.component.CategoryMark
import org.idejo.app.core.designsystem.component.CategoryMarkSize
import org.idejo.app.core.designsystem.component.TopicItem
import org.idejo.app.core.designsystem.component.relativeTime
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Category

/** The home page of the forum open (BR-01): **Categories**, **Active topics**, **Unread**. */
@Composable
fun HomeRoute(
    onCategory: (String) -> Unit,
    onTopic: (String) -> Unit,
    onTag: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Counts the calls to show **Categories**: each new one turns back to it. */
    categoriesCalls: Int = 0,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        categoriesCalls = categoriesCalls,
        state = state,
        onCategory = onCategory,
        onTopic = onTopic,
        onTag = onTag,
        onRefresh = viewModel::refresh,
        onView = viewModel::view,
        modifier = modifier,
        unread = { UnreadRoute(onTopic = onTopic, onTag = onTag, titled = false) },
    )
}

/** The home page's tabs, in their order. */
enum class HomeTab(
    @param:StringRes val title: Int,
) {
    Categories(R.string.home_categories),
    Active(R.string.home_active),
    Unread(R.string.unread_title),
}

/**
 * The home page as three tabs, swiped between as the inbox's (IB-01), in the forum's colours:
 * the categories as cards in their groups, two or more to a row (BR-01); **Active topics**
 * with **View** (BR-03); and [unread] (BR-06). Pull to refresh (BR-10).
 */
@Composable
fun HomeScreen(
    state: HomeUi,
    onCategory: (String) -> Unit,
    onTopic: (String) -> Unit,
    onTag: (String) -> Unit,
    onRefresh: () -> Unit,
    onView: (TopicView) -> Unit,
    modifier: Modifier = Modifier,
    unread: @Composable () -> Unit = {},
    initialTab: HomeTab = HomeTab.Categories,
    categoriesCalls: Int = 0,
) {
    val pager = rememberPagerState(initialPage = initialTab.ordinal) { HomeTab.entries.size }
    var answered by rememberSaveable { mutableIntStateOf(categoriesCalls) }
    LaunchedEffect(categoriesCalls) {
        if (categoriesCalls != answered) {
            answered = categoriesCalls
            pager.scrollToPage(HomeTab.Categories.ordinal)
        }
    }
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = pager.currentPage) {
            HomeTab.entries.forEach { each ->
                Tab(
                    selected = each.ordinal == pager.currentPage,
                    onClick = { scope.launch { pager.animateScrollToPage(each.ordinal) } },
                    text = { Text(stringResource(each.title)) },
                )
            }
        }
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            when (HomeTab.entries[page]) {
                HomeTab.Categories -> CategoriesTab(state, onCategory, onRefresh)
                HomeTab.Active -> ActiveTab(state, onTopic, onTag, onRefresh, onView)
                HomeTab.Unread -> unread()
            }
        }
    }
}

/** The categories as cards in their groups, as many to a row as fit, two on a phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriesTab(
    state: HomeUi,
    onCategory: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val home = state.home
    PullToRefreshBox(home.refreshing, onRefresh, Modifier.fillMaxSize()) {
        if (home.loading) {
            Loading()
            return@PullToRefreshBox
        }
        LazyVerticalGrid(
            GridCells.Adaptive(CARD_WIDTH),
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            home.problem?.let { problem ->
                item(key = "problem", span = { GridItemSpan(maxLineSpan) }) {
                    ProblemRow(problem, onRefresh)
                }
            }
            home.sections.forEachIndexed { index, section ->
                item(key = "section:$index", span = { GridItemSpan(maxLineSpan) }) {
                    SectionTitle(
                        when {
                            home.sections.size == 1 -> stringResource(R.string.home_categories)
                            section.group == null -> stringResource(R.string.home_other)
                            else -> section.group!!.name
                        },
                    )
                }
                items(section.categories, key = { "category:${it.category.id}" }) { entry ->
                    CategoryCard(
                        entry.category,
                        onClick = { onCategory(entry.category.id) },
                        modifier = Modifier.fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/** **Active topics** with **View** beside their title (BR-03). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveTab(
    state: HomeUi,
    onTopic: (String) -> Unit,
    onTag: (String) -> Unit,
    onRefresh: () -> Unit,
    onView: (TopicView) -> Unit,
) {
    val home = state.home
    val picture = remember(state.origin) { pictures(state.origin) }
    PullToRefreshBox(home.refreshing, onRefresh, Modifier.fillMaxSize()) {
        if (home.loading) {
            Loading()
            return@PullToRefreshBox
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            home.problem?.let { problem ->
                item(key = "problem") { ProblemRow(problem, onRefresh) }
            }
            item(key = "view") { ViewToggle(state.view, onView) }
            items(home.active, key = { "topic:${it.id}" }) { topic ->
                TopicItem(
                    topic = topic,
                    onClick = { onTopic(topic.id) },
                    expanded = state.view == TopicView.Expanded,
                    category = home.category(topic.categoryId),
                    picture = picture,
                    onTag = { tag -> tag.slug?.let(onTag) },
                )
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** A category's card (BR-01): its mark and name, description, topics and last activity. */
@Composable
internal fun CategoryCard(
    category: Category,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = IdejoTheme.colors.category(category, MaterialTheme.colorScheme)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color.copy(alpha = LINE)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryMark(category, size = CategoryMarkSize.Card)
                Text(
                    category.title,
                    Modifier.semantics { heading() },
                    style = IdejoTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            category.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = IdejoTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val stats =
                listOfNotNull(
                    pluralStringResource(
                        R.plurals.category_topics,
                        category.topicsCount,
                        category.topicsCount,
                    ),
                    category.lastActivityAt?.let { relativeTime(it) },
                )
            Text(
                stats.joinToString(" · "),
                style = IdejoTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val LINE = 0.5f

/** The narrowest a category's card gets: two to a row on a phone, more on a tablet. */
private val CARD_WIDTH = 150.dp
