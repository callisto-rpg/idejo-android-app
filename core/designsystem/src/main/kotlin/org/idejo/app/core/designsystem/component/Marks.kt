// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.designsystem.theme.onColorFor

/** The sizes of a category's tile: in the breadcrumbs, the sidebar (SH-38) and on cards. */
enum class CategoryMarkSize(
    val tile: Dp,
    val corner: Dp,
    val icon: Dp,
) {
    Breadcrumb(26.dp, 7.dp, 15.dp),
    Sidebar(34.dp, 9.dp, 19.dp),
    Card(40.dp, 11.dp, 22.dp),
}

/**
 * A category's tile (TH-05): its colour, its icon in white or `#111111` (whichever reads
 * better), or its initial when it has no icon. [icon] draws the icon in the tint it is given.
 */
@Composable
fun CategoryMark(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: CategoryMarkSize = CategoryMarkSize.Sidebar,
    icon: (@Composable (tint: Color) -> Unit)? = null,
) {
    val tint = onColorFor(color)
    Box(
        modifier
            .size(size.tile)
            .clip(RoundedCornerShape(size.corner))
            .background(color)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Box(Modifier.size(size.icon), contentAlignment = Alignment.Center) { icon(tint) }
        } else {
            Initial(name, tint, (size.tile.value * INITIAL_SCALE).sp)
        }
    }
}

/**
 * A forum's logo in the top bar and menus (SH-01): [image] when there is one, else the
 * forum's initial on its primary colour.
 */
@Composable
fun ForumLogo(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    image: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * LOGO_CORNER))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            image()
        } else {
            Initial(name, MaterialTheme.colorScheme.onPrimary, (size.value * INITIAL_SCALE).sp)
        }
    }
}

/**
 * A person's picture (TV-02): [image] when they have one (see `pictureUrl`, TH-10), else
 * their initials on [color]. Decorative unless given a [contentDescription].
 */
@Composable
fun Avatar(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    contentDescription: String? = null,
    image: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            image()
        } else {
            Text(
                text = initials(name),
                color = onColorFor(color),
                style = IdejoTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * AVATAR_TEXT).sp,
            )
        }
    }
}

@Composable
private fun Initial(
    name: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    Text(
        text = name.trim().take(1).uppercase(),
        color = color,
        style = IdejoTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
    )
}

/** Up to two initials: "Ada Lovelace" gives "AL". */
internal fun initials(name: String): String =
    name
        .split(' ', '-', '_', '.')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.take(1).uppercase() }
        .ifEmpty { "?" }

private const val INITIAL_SCALE = 0.56f
private const val LOGO_CORNER = 0.29f
private const val AVATAR_TEXT = 0.4f
