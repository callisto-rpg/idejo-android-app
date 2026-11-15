// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import coil3.compose.AsyncImage
import org.idejo.app.core.model.Emoji

/** The smiley's picker (CO-10): the forum's emoji, searched by name; one goes in as `:name:`. */
@Composable
internal fun EmojiPicker(
    origin: String,
    emoji: suspend (String) -> List<Emoji>,
    onPick: (Emoji) -> Unit,
    onDismiss: () -> Unit,
) = org.idejo.app.core.designsystem.component.EmojiPicker(emoji, onPick, onDismiss) { url ->
    AsyncImage(absolute(origin, url), null, Modifier.fillMaxSize())
}
