// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.data.PagesState
import org.idejo.app.core.data.TopicView
import org.idejo.app.core.designsystem.theme.IdejoTheme

/** A section's title with a line across, as the web's `.section-divider`. */
@Composable
internal fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            Modifier.semantics { heading() },
            style = IdejoTheme.typography.titleLarge,
        )
        HorizontalDivider(Modifier.weight(1f))
        trailing()
    }
}

/** A page's title, as the web's `h1`. */
@Composable
internal fun PageTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        modifier.semantics { heading() },
        style = IdejoTheme.typography.headlineSmall,
    )
}

/** **View**: Compact or Expanded, as two joined buttons (BR-03). */
@Composable
internal fun ViewToggle(
    view: TopicView,
    onView: (TopicView) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.view_label),
            style = IdejoTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow {
            val choices =
                listOf(
                    TopicView.Compact to R.string.view_compact,
                    TopicView.Expanded to R.string.view_expanded,
                )
            choices.forEachIndexed { index, (choice, label) ->
                SegmentedButton(
                    selected = view == choice,
                    onClick = { onView(choice) },
                    shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                    icon = {},
                ) { Text(stringResource(label), style = IdejoTheme.typography.labelMedium) }
            }
        }
    }
}

/** Why a list could not be read, with **Try again**: the server's words when it gave some. */
@Composable
internal fun ProblemRow(
    problem: ApiProblem,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when (problem) {
                is ApiProblem.Server -> problem.error.message
                is ApiProblem.Network -> stringResource(R.string.list_offline)
                is ApiProblem.Malformed -> stringResource(R.string.list_could_not_load)
            },
            Modifier.weight(1f),
            style = IdejoTheme.typography.bodyMedium,
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.list_try_again)) }
    }
}

/**
 * The end of a paged list: the first page coming, [empty] when nothing came, why a page
 * failed with **Try again**, and the spinner while more comes.
 */
internal fun <T> LazyListScope.pagedEnd(
    pages: PagesState<T>,
    empty: String?,
    onRefresh: () -> Unit,
    onMore: () -> Unit,
) {
    when {
        pages.loading -> {
            item(key = "loading") { MoreSpinner() }
        }

        pages.items.isEmpty() && pages.problem == null && empty != null -> {
            item(key = "empty") {
                Text(
                    empty,
                    style = IdejoTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    pages.problem?.let { problem ->
        item(key = "problem") {
            ProblemRow(problem, if (pages.items.isEmpty()) onRefresh else onMore)
        }
    }
    if (pages.loadingMore) item(key = "more") { MoreSpinner() }
}

/** The spinner at the end of a list while its next page comes. */
@Composable
internal fun MoreSpinner(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Asks for more once the reader is within a few rows of the end (BR-10). */
@Composable
internal fun EndlessScroll(
    list: LazyListState,
    onMore: () -> Unit,
) {
    val more by rememberUpdatedState(onMore)
    val near by remember {
        derivedStateOf {
            val info = list.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - AHEAD
        }
    }
    LaunchedEffect(near) { if (near) more() }
}

/** Pictures from the forum at [origin] (addresses may be relative), cropped to their box. */
internal fun pictures(origin: String): @Composable (String, Modifier) -> Unit =
    { url, modifier ->
        AsyncImage(absolute(origin, url), null, modifier, contentScale = ContentScale.Crop)
    }

/** [url] as an absolute address: a path is the forum's. */
internal fun absolute(
    origin: String,
    url: String,
): String = if (url.startsWith("/")) origin.trimEnd('/') + url else url

private const val AHEAD = 5
