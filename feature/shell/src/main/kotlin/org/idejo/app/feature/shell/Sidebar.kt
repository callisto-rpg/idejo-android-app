// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.idejo.app.core.data.SidebarCategory
import org.idejo.app.core.data.SidebarState
import org.idejo.app.core.designsystem.component.CategoryMark
import org.idejo.app.core.designsystem.component.CategoryMarkSize
import org.idejo.app.core.designsystem.component.Dot
import org.idejo.app.core.designsystem.component.dot
import org.idejo.app.core.designsystem.component.unreadLevel
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.designsystem.theme.onSecondaryClick
import org.idejo.app.core.designsystem.theme.parseHexColor
import org.idejo.app.core.model.Topic

/** What the member does in the sidebar. */
interface SidebarActions {
    fun open(place: Place)

    fun fold(categoryId: String)

    fun rollLinks(rolled: Boolean)

    fun follow(
        topic: Topic,
        followed: Boolean,
    )

    fun unfollowAndMute(topic: Topic)

    fun markUnread(topic: Topic)

    fun copyLink(topic: Topic)

    fun share(topic: Topic)

    fun retry()
}

/**
 * The left sidebar (SH-31 to SH-38): **All categories** and the links above the categories
 * (rolled up into icons or not), then each category in the home page's order with the
 * topics followed there, their dots, glows, **×** and **+**, and a menu on long press.
 */
@Composable
fun Sidebar(
    state: SidebarState,
    folded: Set<String>,
    linksRolled: Boolean,
    signedIn: Boolean,
    openTopic: Topic?,
    actions: SidebarActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    LazyColumn(modifier, contentPadding = contentPadding) {
        item(key = "all") { AllCategories(linksRolled, actions) }
        if (!linksRolled) {
            links.forEach { link ->
                item(key = link.place.toString()) {
                    LinkRow(link) { actions.open(link.place) }
                }
            }
        }
        if (state.problem != null && state.sections.isEmpty()) {
            item(key = "problem") { Problem(actions::retry) }
        }
        state.sections.forEach { section ->
            section.group?.let { group ->
                item(key = "group:${group.id}") { GroupHeading(group.name) }
            }
            section.categories.forEach { entry ->
                category(
                    entry = entry,
                    folded = entry.category.id in folded,
                    expanded = entry.category.id in expanded,
                    glowing = state.glowing,
                    openTopic = openTopic,
                    signedIn = signedIn,
                    actions = actions,
                    onExpand = { expanded = expanded + entry.category.id },
                    offline = state.offline,
                )
            }
        }
    }
}

@Suppress("LongParameterList") // One category's rows and what changes them.
private fun LazyListScope.category(
    entry: SidebarCategory,
    folded: Boolean,
    expanded: Boolean,
    glowing: Map<String, Int>,
    openTopic: Topic?,
    signedIn: Boolean,
    actions: SidebarActions,
    onExpand: () -> Unit,
    /** Not queued offline (OF-05): follows and Mark unread wait. */
    offline: Boolean = false,
) {
    val id = entry.category.id
    item(key = "category:$id") {
        CategoryRow(entry, folded, showFold = signedIn, actions)
    }
    val open = openTopic?.takeIf { it.categoryId == id }
    var topics = entry.topics
    // The topic open is listed even when not followed, with a + (SH-34).
    if (open != null && topics.none { it.id == open.id }) topics = topics + open
    if (folded) topics = topics.filter { it.dot() != null || it.id == open?.id }
    val shown = if (expanded || topics.size <= SHOWN) topics else topics.take(SHOWN)
    shown.forEach { topic ->
        item(key = "topic:$id:${topic.id}") {
            TopicRow(
                topic = topic,
                open = topic.id == open?.id,
                followed = entry.topics.any { it.id == topic.id },
                glow = glowing[topic.id],
                actions = actions,
                offline = offline,
            )
        }
    }
    if (shown.size < topics.size) {
        item(key = "more:$id") { MoreRow(topics.size - shown.size, onExpand) }
    }
}

@Composable
private fun AllCategories(
    rolled: Boolean,
    actions: SidebarActions,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(if (rolled) R.string.sidebar_all else R.string.sidebar_all_categories),
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .combinedClickable(role = Role.Button) { actions.open(Place.Home) }
                .heightIn(min = TARGET)
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .semantics { heading() },
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        if (rolled) {
            links.forEach { link ->
                IconButton(onClick = { actions.open(link.place) }, Modifier.size(TARGET)) {
                    Icon(painterResource(link.icon), stringResource(link.label))
                }
            }
        }
        IconButton(onClick = { actions.rollLinks(!rolled) }, Modifier.size(TARGET)) {
            Icon(
                painterResource(if (rolled) R.drawable.ic_arrow_down else R.drawable.ic_arrow_up),
                stringResource(if (rolled) R.string.sidebar_unroll else R.string.sidebar_roll_up),
            )
        }
    }
}

@Composable
private fun LinkRow(
    link: Link,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .combinedClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(link.icon), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(link.label), style = TEXT)
    }
}

@Composable
private fun GroupHeading(name: String) {
    Text(
        name.uppercase(),
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CategoryRow(
    entry: SidebarCategory,
    folded: Boolean,
    showFold: Boolean,
    actions: SidebarActions,
) {
    val category = entry.category
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CATEGORY_HEIGHT)
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = CATEGORY_HEIGHT)
                .combinedClickable(role = Role.Button) { actions.open(Place.Category(category.id)) }
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryMark(category, size = CategoryMarkSize.Sidebar)
            Text(
                category.title,
                Modifier.weight(1f).semantics { heading() },
                // In the title font, as titles are everywhere.
                style = IdejoTheme.typography.titleMedium.copy(fontSize = 18.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showFold) {
            IconButton(
                onClick = { actions.fold(category.id) },
                modifier = Modifier.size(TARGET),
            ) {
                Icon(
                    painterResource(
                        if (folded) R.drawable.ic_chevron else R.drawable.ic_arrow_down,
                    ),
                    stringResource(if (folded) R.string.sidebar_unfold else R.string.sidebar_fold),
                )
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopicRow(
    topic: Topic,
    open: Boolean,
    followed: Boolean,
    glow: Int?,
    actions: SidebarActions,
    offline: Boolean,
) {
    var menu by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    // Only the open topic is highlighted; the others show the sidebar through.
    val highlight = if (open) colors.secondaryContainer else Color.Transparent
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = ROW_HEIGHT)
                .padding(start = 8.dp, end = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(highlight)
                .onSecondaryClick { menu = true }
                .combinedClickable(
                    role = Role.Button,
                    onLongClick = { menu = true },
                    onClick = { actions.open(Place.Topic(topic.id)) },
                ).padding(start = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(DOT_SPACE), contentAlignment = Alignment.Center) {
                topic.dot()?.let { Dot(it) }
            }
            GlowingName(topic.title, glow, Modifier.weight(1f))
            if (followed) {
                IconButton(
                    onClick = { actions.follow(topic, false) },
                    Modifier.size(TARGET),
                    enabled = !offline,
                ) {
                    Icon(
                        painterResource(org.idejo.app.core.designsystem.R.drawable.ic_close),
                        stringResource(R.string.sidebar_unfollow, topic.title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (open) {
                IconButton(
                    onClick = { actions.follow(topic, true) },
                    Modifier.size(TARGET),
                    enabled = !offline,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_add),
                        stringResource(R.string.sidebar_follow, topic.title),
                    )
                }
            }
        }
        TopicMenu(topic, followed, menu, onDismiss = { menu = false }, actions, offline)
    }
}

/** SH-37: Follow/Unfollow, Unfollow & mute, Mark unread, Copy link, Share. */
@Composable
private fun TopicMenu(
    topic: Topic,
    followed: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: SidebarActions,
    offline: Boolean,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        fun pick(action: () -> Unit) {
            onDismiss()
            action()
        }
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (followed) R.string.topic_menu_unfollow else R.string.topic_menu_follow,
                    ),
                )
            },
            onClick = { pick { actions.follow(topic, !followed) } },
            enabled = !offline,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.topic_menu_unfollow_mute)) },
            onClick = { pick { actions.unfollowAndMute(topic) } },
            enabled = !offline,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.topic_menu_mark_unread)) },
            onClick = { pick { actions.markUnread(topic) } },
            enabled = !offline,
        )
        if (topic.url != null) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.topic_menu_copy_link)) },
                onClick = { pick { actions.copyLink(topic) } },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.topic_menu_share)) },
                onClick = { pick { actions.share(topic) } },
            )
        }
    }
}

@Composable
private fun MoreRow(
    count: Int,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = ROW_HEIGHT).padding(start = 46.dp),
    ) {
        Text(pluralStringResource(R.plurals.sidebar_more_topics, count, count), style = TEXT)
    }
}

@Composable
private fun Problem(onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.sidebar_could_not_load),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.sidebar_try_again)) }
    }
}

/** One of the links above the categories (SH-35). */
internal class Link(
    val place: Place,
    val icon: Int,
    val label: Int,
)

internal val links =
    listOf(
        Link(Place.Tags(), R.drawable.ic_tag, R.string.place_tags),
        Link(Place.Search, R.drawable.ic_search, R.string.place_search),
        Link(Place.Rules, R.drawable.ic_rules, R.string.place_rules),
        Link(Place.Help, R.drawable.ic_help, R.string.place_help),
    )

private const val SHOWN = 4

/** SH-38: rows at least 54 dp, categories 56 dp, each small button a 44 dp target. */
internal val ROW_HEIGHT = 54.dp
private val CATEGORY_HEIGHT = 56.dp
private val TARGET = 44.dp
private val DOT_SPACE = 14.dp

internal val TEXT
    @Composable get() = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp)
