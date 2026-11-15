// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import org.idejo.app.core.designsystem.R
import org.idejo.app.core.model.Emoji

/**
 * The forum's emoji, searched by name (CO-10, MA-03): the composer's smiley, a poll answer's,
 * a reaction's **+**. [image] draws a server emoji from its address.
 */
@Composable
fun EmojiPicker(
    emoji: suspend (String) -> List<Emoji>,
    onPick: (Emoji) -> Unit,
    onDismiss: () -> Unit,
    image: @Composable (String) -> Unit,
) = Dialog(onDismissRequest = onDismiss) {
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf(emptyList<Emoji>()) }
    val source by rememberUpdatedState(emoji)
    LaunchedEffect(query) {
        delay(SEARCH_DELAY_MILLIS)
        found = source(query)
    }
    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.emoji_search)) },
                singleLine = true,
            )
            LazyVerticalGrid(
                GridCells.Adaptive(48.dp),
                Modifier.heightIn(max = 320.dp).padding(top = 8.dp),
            ) {
                items(found, key = { it.name }) { item ->
                    Box(
                        Modifier
                            .size(48.dp)
                            .semantics { contentDescription = ":${item.name}:" }
                            .clickable(role = Role.Button) { onPick(item) },
                        contentAlignment = Alignment.Center,
                    ) {
                        val char = item.char
                        val url = item.imageUrl
                        when {
                            char != null -> {
                                Text(char, fontSize = 24.sp)
                            }

                            url != null -> {
                                Box(Modifier.size(28.dp)) { image(url) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val SEARCH_DELAY_MILLIS = 200L
