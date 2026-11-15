// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Topic

/** **Follow** / **Unfollow** under the title, or "Followed by everyone" (TP-13). */
@Composable
internal fun Follow(
    topic: Topic,
    actions: TopicActions,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (topic.followedByEveryone) {
            Text(
                stringResource(R.string.topic_followed_by_everyone),
                style = IdejoTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (topic.followed) {
            OutlinedButton(onClick = { actions.onFollow(false) }) {
                Text(stringResource(R.string.topic_unfollow))
            }
        } else {
            Button(
                onClick = { actions.onFollow(true) },
            ) { Text(stringResource(R.string.topic_follow)) }
        }
    }
}
