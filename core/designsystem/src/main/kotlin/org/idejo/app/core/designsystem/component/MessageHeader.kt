// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.idejo.app.core.designsystem.theme.IdejoTheme

/**
 * The line above a message (TV-02): the author's picture, display name in their role's
 * colour (made readable, TH-05), user tag, role, and the time at the right.
 */
@Composable
fun MessageHeader(
    displayName: String,
    time: String,
    modifier: Modifier = Modifier,
    /** The name's own actions (a tap opens the user card). */
    nameModifier: Modifier = Modifier,
    /** The time's own actions (a long press copies the permalink). */
    timeModifier: Modifier = Modifier,
    userTag: String? = null,
    role: String? = null,
    roleColor: Color? = null,
    avatar: @Composable () -> Unit = {},
) {
    val soft = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        avatar()
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                displayName,
                nameModifier.weight(1f, fill = false),
                color = IdejoTheme.colors.roleName(roleColor),
                style = IdejoTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!userTag.isNullOrBlank()) {
                Text(userTag, color = soft, style = IdejoTheme.typography.labelMedium, maxLines = 1)
            }
            if (!role.isNullOrBlank()) {
                Text(role, color = soft, style = IdejoTheme.typography.labelMedium, maxLines = 1)
            }
        }
        Text(
            time,
            timeModifier,
            color = soft,
            style = IdejoTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}
