// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.idejo.app.core.designsystem.component.ForumLogo
import org.idejo.app.core.designsystem.theme.IdejoTheme

/**
 * Another forum of the group, as idejo ID mode shows it: logo (or initial), name, address,
 * **Join** and **Open**. Both wait for idejo ID mode (M4): shown, not yet enabled (steward
 * decision, 2026-11-14). For `&forum` links (TV-22) and `forum` blocks (TV-26).
 */
@Composable
internal fun ForumCard(
    name: String,
    host: String,
    logo: String?,
    origin: String,
    modifier: Modifier = Modifier,
) = Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ForumLogo(
            name,
            size = 40.dp,
            image =
                logo?.let { url ->
                    { AsyncImage(absolute(origin, url), null, contentScale = ContentScale.Crop) }
                },
        )
        Column {
            Text(name, style = IdejoTheme.typography.titleMedium)
            Text(
                host,
                style = IdejoTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val join = stringResource(R.string.forum_join_named, name)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {}, Modifier.semantics { contentDescription = join }, enabled = false) {
            Text(stringResource(R.string.forum_join))
        }
        OutlinedButton(onClick = {}, enabled = false) { Text(stringResource(R.string.forum_open)) }
    }
    Text(
        stringResource(R.string.forum_later),
        style = IdejoTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
