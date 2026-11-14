// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.idejo.app.core.designsystem.theme.IdejoTheme

/** A topic's name, glowing briefly when a message arrives (SH-32); still with reduced motion. */
@Composable
internal fun GlowingName(
    title: String,
    glow: Int?,
    modifier: Modifier = Modifier,
) {
    val reduced = IdejoTheme.reducedMotion
    val strength = remember { Animatable(0f) }
    LaunchedEffect(glow) {
        if (glow == null || reduced) return@LaunchedEffect
        strength.snapTo(1f)
        strength.animateTo(0f, tween(GLOW_MILLIS))
    }
    Text(
        title,
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = GLOW_ALPHA * strength.value))
            .padding(horizontal = 4.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

private const val GLOW_MILLIS = 1600
private const val GLOW_ALPHA = 0.35f
