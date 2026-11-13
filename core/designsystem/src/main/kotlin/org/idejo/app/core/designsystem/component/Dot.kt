// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.idejo.app.core.designsystem.R
import org.idejo.app.core.designsystem.theme.DotKind
import org.idejo.app.core.designsystem.theme.IdejoTheme

/**
 * The dot before a topic (SH-32): grey new messages, blue a `+role` mentioned, orange a
 * reply, red a mention. With [ring], a ring of that colour around it, as on the logo
 * (SH-03). TalkBack reads what it means.
 */
@Composable
fun Dot(
    kind: DotKind,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    ring: Color? = null,
) {
    val description = stringResource(kind.description)
    val dot = Modifier.background(IdejoTheme.colors.dot(kind), CircleShape)
    Box(
        modifier
            .semantics { contentDescription = description }
            .then(
                if (ring == null) {
                    Modifier.size(size).then(dot)
                } else {
                    Modifier
                        .size(size + RING * 2)
                        .background(ring, CircleShape)
                        .padding(RING)
                        .then(dot)
                },
            ),
    )
}

/** A dot of any colour, without words: the kind is said beside it (a bubble's title). */
@Composable
internal fun ColorDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
) {
    Box(modifier.size(size).background(color, CircleShape))
}

internal val DotKind.description: Int
    get() =
        when (this) {
            DotKind.NewMessages -> R.string.dot_new_messages
            DotKind.RoleMention -> R.string.dot_role_mention
            DotKind.Reply -> R.string.dot_reply
            DotKind.Mention -> R.string.dot_mention
        }

private val RING = 2.dp
