// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.markdown

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.idejo.app.core.designsystem.theme.IdejoTheme

/**
 * A message marked as a spoiler (TV-23, the message's `spoiler`): its body blurred at half
 * opacity under **Reveal spoiler** and the author's [overlay] words, as the web's cover.
 * Hidden, the body is neither read by TalkBack nor tappable.
 */
@Composable
fun SpoilerCover(
    revealed: Boolean,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: String? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            if (revealed) {
                Modifier
            } else {
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(COVERED)
                    .blur(BLUR)
            },
        ) { content() }
        if (revealed) return@Box
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // No blur before Android 12: a veil instead, so the words do not show.
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = VEIL)),
            )
        }
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!overlay.isNullOrBlank()) {
                Text(
                    overlay,
                    style = IdejoTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                )
            }
            // The web's Reveal spoiler: the card colour at 80 %, over the blurred body.
            FilledTonalButton(
                onClick = onReveal,
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = BUTTON),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            ) {
                Text(stringResource(R.string.reveal_spoiler))
            }
        }
    }
}

private const val COVERED = 0.5f
private const val VEIL = 0.92f
private const val BUTTON = 0.8f

/** As the web's default stylesheet (`--spoiler-blur`). */
private val BLUR = 32.dp
