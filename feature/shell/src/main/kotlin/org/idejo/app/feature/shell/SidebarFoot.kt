// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import org.idejo.app.core.designsystem.theme.IdejoTheme

/**
 * **Private Conversations** at the very bottom (PC-01, SH-36), with the number of
 * conversations with something new.
 */
@Composable
internal fun ConversationsRow(
    withNews: Int,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.place_conversations)
    val news = pluralStringResource(R.plurals.conversations_with_news, withNews, withNews)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .clearAndSetSemantics {
                contentDescription = if (withNews > 0) "$label, $news" else label
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            }.combinedClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_chat),
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(label, Modifier.weight(1f), style = TEXT)
        if (withNews > 0) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                Text(
                    withNews.toString(),
                    Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = IdejoTheme.typography.labelLarge,
                )
            }
        }
    }
}
