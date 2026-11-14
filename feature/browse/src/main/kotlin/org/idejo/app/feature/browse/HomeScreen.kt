// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import org.idejo.app.core.data.TopicView
import org.idejo.app.core.designsystem.component.CategoryMark
import org.idejo.app.core.designsystem.component.CategoryMarkSize
import org.idejo.app.core.designsystem.component.TopicItem
import org.idejo.app.core.designsystem.component.relativeTime
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Category

/** The home page of the forum open (BR-01). */
@Composable
fun HomeRoute(
    onCategory: (String) -> Unit,
    onTopic: (String) -> Unit,
    onTag: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onCategory = onCategory,
        onTopic = onTopic,
        onTag = onTag,
        onRefresh = viewModel::refresh,
        onView = viewModel::view,
        modifier = modifier,
    )
}

/**
 * The categories as cards in their groups (number of topics, last activity), then **Active
 * topics** with **View** beside their title (BR-01, BR-03); pull to refresh (BR-10).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUi,
    onCategory: (String) -> Unit,
    onTopic: (String) -> Unit,
    onTag: (String) -> Unit,
    onRefresh: () -> Unit,
    onView: (TopicView) -> Unit,
    modifier: Modifier = Modifier,
) {
    val home = state.home
    val picture = remember(state.origin) { pictures(state.origin) }
    PullToRefreshBox(home.refreshing, onRefresh, modifier.fillMaxSize()) {
        if (home.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
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
            home.sections.forEachIndexed { index, section ->
                item(key = "section:$index") {
                    SectionTitle(
                        when {
                            home.sections.size == 1 -> stringResource(R.string.home_categories)
                            section.group == null -> stringResource(R.string.home_other)
                            else -> section.group!!.name
                        },
                    )
                }
                items(section.categories, key = { "category:${it.category.id}" }) { entry ->
                    CategoryCard(entry.category, onClick = { onCategory(entry.category.id) })
                }
            }
            if (home.active.isNotEmpty()) {
                item(key = "active") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle(stringResource(R.string.home_active))
                        ViewToggle(state.view, onView)
                    }
                }
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
}

/** A category's card (BR-01): its mark and name, description, topics and last activity. */
@Composable
internal fun CategoryCard(
    category: Category,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = IdejoTheme.colors.category(category.colour, MaterialTheme.colorScheme)
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
                CategoryMark(category.title, color, size = CategoryMarkSize.Card)
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
