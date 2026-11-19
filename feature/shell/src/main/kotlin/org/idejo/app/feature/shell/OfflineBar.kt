// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import android.text.format.DateUtils
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.idejo.app.core.designsystem.theme.IdejoTheme

/**
 * The forum cannot be reached (OF-02): the page shows what was saved at [savedAt], and says
 * so above it, with why the actions that need the forum do nothing yet.
 */
@Composable
internal fun OfflineBar(
    savedAt: Long?,
    modifier: Modifier = Modifier,
) {
    if (savedAt == null) return
    val context = LocalContext.current
    val today = DateUtils.isToday(savedAt)
    val time =
        DateUtils.formatDateTime(
            context,
            savedAt,
            if (today) {
                DateUtils.FORMAT_SHOW_TIME
            } else {
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME
            },
        )
    Surface(
        modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            stringResource(R.string.offline_saved_copy, time),
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = IdejoTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
