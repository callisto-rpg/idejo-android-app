// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.idejo.app.core.designsystem.R
import org.idejo.app.core.designsystem.theme.DotKind
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.designsystem.theme.onColorFor

/** One crumb of the breadcrumbs (SH-02). */
@Immutable
sealed interface Crumb {
    /** The forum's logo: it opens the logo menu (SH-10). */
    data class Forum(
        val name: String,
        val onClick: () -> Unit,
        val logo: (@Composable () -> Unit)? = null,
    ) : Crumb

    /** A category's tile. */
    data class Category(
        val name: String,
        val color: Color,
        val onClick: () -> Unit,
        val icon: (@Composable (tint: Color) -> Unit)? = null,
    ) : Crumb

    /** The page: a topic's title, "Private Conversations", a person's name. One line. */
    data class Page(
        val title: String,
        val onClick: (() -> Unit)? = null,
        val onLongClick: (() -> Unit)? = null,
    ) : Crumb
}

/** The **Unread** button's count and colour (SH-01): the strongest of what it counts. */
@Immutable
data class Unread(
    val count: Int,
    val strongest: DotKind?,
    val onClick: () -> Unit,
)

/**
 * The breadcrumbs as the web's on a phone (SH-02): the forum's logo, the category's tile,
 * the page's title on one line (long-press shows it whole, given [Crumb.Page.onLongClick]).
 */
@Composable
fun Breadcrumbs(
    crumbs: List<Crumb>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        crumbs.forEachIndexed { index, crumb ->
            if (index > 0) Separator()
            when (crumb) {
                is Crumb.Forum -> ForumCrumb(crumb)
                is Crumb.Category -> CategoryCrumb(crumb)
                is Crumb.Page -> PageCrumb(crumb, Modifier.weight(1f, fill = false))
            }
        }
    }
}

/**
 * The forum's top bar (SH-01): the sidebar button, the breadcrumbs, **Unread** with its
 * count and colour, and the member's [account] picture. With a [scrollBehavior] it hides on
 * scroll down and returns on scroll up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    crumbs: List<Crumb>,
    modifier: Modifier = Modifier,
    onSidebarClick: (() -> Unit)? = null,
    unread: Unread? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    account: @Composable () -> Unit = {},
) {
    val background = MaterialTheme.colorScheme.background
    Column(modifier) {
        TopAppBar(
            title = { Breadcrumbs(crumbs) },
            navigationIcon = {
                if (onSidebarClick != null) {
                    IconButton(onClick = onSidebarClick) {
                        Icon(
                            painterResource(R.drawable.ic_menu),
                            stringResource(R.string.top_bar_sidebar),
                        )
                    }
                }
            },
            actions = {
                if (unread != null) UnreadButton(unread)
                Box(Modifier.padding(end = 8.dp)) { account() }
            },
            expandedHeight = 56.dp,
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    scrolledContainerColor = background,
                ),
            scrollBehavior = scrollBehavior,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun Separator() {
    Text(
        "›",
        Modifier.clearAndSetSemantics {},
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = IdejoTheme.typography.bodyLarge,
    )
}

@Composable
private fun ForumCrumb(crumb: Crumb.Forum) {
    val label = stringResource(R.string.top_bar_forum_menu, crumb.name)
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(role = Role.Button, onClickLabel = label, onClick = crumb.onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        ForumLogo(crumb.name, image = crumb.logo)
    }
}

@Composable
private fun CategoryCrumb(crumb: Crumb.Category) {
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(role = Role.Button, onClick = crumb.onClick),
        contentAlignment = Alignment.Center,
    ) {
        CategoryMark(crumb.name, crumb.color, size = CategoryMarkSize.Breadcrumb, icon = crumb.icon)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageCrumb(
    crumb: Crumb.Page,
    modifier: Modifier = Modifier,
) {
    val clickable =
        if (crumb.onClick != null || crumb.onLongClick != null) {
            Modifier.combinedClickable(
                onClick = crumb.onClick ?: {},
                onLongClick = crumb.onLongClick,
            )
        } else {
            Modifier
        }
    Text(
        crumb.title,
        modifier
            .heightIn(min = 48.dp)
            .then(clickable)
            .semantics { heading() }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        style = IdejoTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun UnreadButton(unread: Unread) {
    if (unread.count <= 0) {
        TextButton(onClick = unread.onClick) {
            Text(stringResource(R.string.top_bar_unread), style = IdejoTheme.typography.labelLarge)
        }
        return
    }
    val color = IdejoTheme.colors.dot(unread.strongest ?: DotKind.NewMessages)
    val description =
        pluralStringResource(R.plurals.top_bar_unread_count, unread.count, unread.count)
    Surface(
        onClick = unread.onClick,
        modifier =
            Modifier
                .minimumInteractiveComponentSize()
                .semantics { contentDescription = description },
        shape = RoundedCornerShape(12.dp),
        color = color,
        contentColor = onColorFor(color),
    ) {
        Text(
            unread.count.toString(),
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = IdejoTheme.typography.labelLarge,
        )
    }
}
