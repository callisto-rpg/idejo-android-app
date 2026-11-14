// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.idejo.app.core.designsystem.component.relativeTime
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Category
import org.idejo.app.core.model.SearchHit

/** **Search** (BR-05); a hit opens its message. */
@Composable
fun SearchRoute(
    onMessage: (topicId: String, messageId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreen(
        state = state,
        actions =
            SearchActions(
                onWords = viewModel::words,
                onCategory = viewModel::category,
                onSearch = viewModel::search,
                onMessage = onMessage,
                onRefresh = viewModel::refresh,
                onMore = viewModel::more,
            ),
        modifier = modifier,
    )
}

/** What the member does on **Search**. */
class SearchActions(
    val onWords: (String) -> Unit,
    val onCategory: (String?) -> Unit,
    val onSearch: () -> Unit,
    val onMessage: (topicId: String, messageId: String) -> Unit,
    val onRefresh: () -> Unit,
    val onMore: () -> Unit,
)

/**
 * **Words to find**, **Category** and **Search**, then the messages found: their topic, the
 * words around the matches with the matches marked, the category, who wrote and when, a page
 * at a time (BR-05, BR-10). Search never shows private conversations: the server's rule.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUi,
    actions: SearchActions,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    val hits = state.hits
    val nothing = stringResource(R.string.search_nothing)
    EndlessScroll(list, actions.onMore)
    PullToRefreshBox(hits.refreshing, actions.onRefresh, modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "form") { Form(state, actions) }
            items(hits.items, key = { it.messageId }) { hit ->
                Hit(hit, state.categories.find(hit.categoryId)) {
                    actions.onMessage(hit.topicId, hit.messageId)
                }
            }
            if (state.searched != null) {
                pagedEnd(
                    hits,
                    nothing,
                    actions.onRefresh,
                    actions.onMore,
                )
            }
        }
    }
}

@Composable
private fun Form(
    state: SearchUi,
    actions: SearchActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageTitle(stringResource(R.string.search_title))
        OutlinedTextField(
            value = state.query.words,
            onValueChange = actions.onWords,
            modifier = Modifier.fillMaxWidth().semantics { traversalIndex = -1f },
            label = { Text(stringResource(R.string.search_words)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { actions.onSearch() }),
        )
        CategoryChoice(state.categories, state.query.categoryId, actions.onCategory)
        Button(onClick = actions.onSearch, enabled = state.query.words.isNotBlank()) {
            Text(stringResource(R.string.search_title))
        }
    }
}

/** **Category**: **All categories** or one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChoice(
    categories: List<Category>,
    chosen: String?,
    onChoose: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val all = stringResource(R.string.search_all_categories)
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(
            value = categories.find(chosen)?.title ?: all,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.search_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text(all) }, onClick = {
                onChoose(null)
                open = false
            })
            categories.forEach { category ->
                DropdownMenuItem(text = { Text(category.title) }, onClick = {
                    onChoose(category.id)
                    open = false
                })
            }
        }
    }
}

/** A message found: its topic, the excerpt with its matches marked, and where it stands. */
@Composable
private fun Hit(
    hit: SearchHit,
    category: Category?,
    onClick: () -> Unit,
) {
    val mark =
        SpanStyle(
            background = MaterialTheme.colorScheme.tertiaryContainer,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(hit.topicTitle, style = IdejoTheme.typography.titleMedium)
            Text(
                buildAnnotatedString {
                    hit.excerpt.forEach { segment ->
                        if (segment.match) {
                            withStyle(
                                mark,
                            ) { append(segment.text) }
                        } else {
                            append(segment.text)
                        }
                    }
                },
                style = IdejoTheme.typography.bodyMedium,
            )
            Text(
                listOfNotNull(
                    category?.title,
                    hit.authorName,
                    hit.openedAt?.let { relativeTime(it) },
                ).joinToString(" · "),
                style = IdejoTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
