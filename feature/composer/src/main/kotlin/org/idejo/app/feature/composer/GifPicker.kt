// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.idejo.app.core.api.Gif
import org.idejo.app.core.api.GifPage
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.GifConfig

/**
 * `!gif` and the toolbar's GIF (CO-15): the provider's popular GIFs, else the search's, in
 * its order; its name under the results, its own wording in the search box.
 */
@Composable
internal fun GifPicker(
    config: GifConfig,
    page: suspend (String?, Int) -> GifPage?,
    onPick: (Gif, String?) -> Unit,
    onDismiss: () -> Unit,
) = Dialog(onDismissRequest = onDismiss) {
    var query by remember { mutableStateOf("") }
    val results = rememberGifResults(query, page)
    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth(),
                placeholder = {
                    Text(config.placeholder ?: stringResource(R.string.composer_gif_search))
                },
                singleLine = true,
            )
            if (results.failed) {
                Text(
                    stringResource(R.string.composer_gif_failed),
                    Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            LazyVerticalGrid(
                GridCells.Adaptive(96.dp),
                Modifier.heightIn(max = 360.dp).padding(top = 8.dp),
            ) {
                itemsIndexed(results.found, key = { _, gif -> gif.id }) { i, gif ->
                    if (results.more && i == results.found.lastIndex) {
                        LaunchedEffect(results.found.size) { results.loading = true }
                    }
                    AsyncImage(
                        gif.preview,
                        gif.title.ifBlank { null },
                        Modifier
                            .padding(2.dp)
                            .aspectRatio(1f)
                            .clickable(role = Role.Button) { onPick(gif, query.ifBlank { null }) },
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            config.name?.let {
                Text(
                    stringResource(R.string.composer_gif_by, it),
                    Modifier.padding(top = 8.dp),
                    style = IdejoTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The GIFs found for a query, a page more as the grid's end shows. */
@Stable
private class GifResults {
    var found by mutableStateOf(emptyList<Gif>())
    var more by mutableStateOf(false)
    var next by mutableIntStateOf(1)
    var failed by mutableStateOf(false)
    var loading by mutableStateOf(false)
}

@Composable
private fun rememberGifResults(
    query: String,
    page: suspend (String?, Int) -> GifPage?,
): GifResults {
    val results = remember { GifResults() }
    val source by rememberUpdatedState(page)
    LaunchedEffect(query) {
        delay(SEARCH_DELAY_MILLIS)
        val first = source(query.ifBlank { null }, 1)
        results.failed = first == null
        results.found = first?.items.orEmpty()
        results.more = first?.more == true
        results.next = 2
    }
    LaunchedEffect(results.loading) {
        if (!results.loading) return@LaunchedEffect
        source(query.ifBlank { null }, results.next)?.let {
            results.found = (results.found + it.items).distinctBy { gif -> gif.id }
            results.more = it.more
            results.next++
        }
        results.loading = false
    }
    return results
}

/** The GIF's Markdown, as the web writes it: its title, else "GIF", as the description. */
internal fun gifMarkdown(
    gif: Gif,
    label: String,
): String {
    val alt =
        gif.title
            .replace(Regex("[\\[\\]\\n]"), " ")
            .trim()
            .ifEmpty { label }
    return "![$alt](${gif.full})"
}

private const val SEARCH_DELAY_MILLIS = 300L
