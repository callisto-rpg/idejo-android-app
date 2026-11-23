// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.idejo.app.core.designsystem.component.ForumLogo
import org.idejo.app.core.designsystem.R as DesignR

/** idejo ID mode's part of the logo menu (SH-12 to SH-14). */
@Immutable
data class GroupMenu(
    val forums: List<YourForum> = emptyList(),
    /** The inbox's conversations with something new; null until read. */
    val chatsWithNews: Int? = null,
    /** The group has a directory: **Find more forums** (SI-08). */
    val directory: Boolean = false,
)

/** What the member does in the logo menu beyond **This forum**. */
interface LogoMenuActions : YourForumsActions {
    /** **All my topics** (SH-13): the inbox's Topics. */
    fun topics()

    /** **Private Conversations** (SH-13): the inbox's Chats. */
    fun chats()

    /** **Find more forums** (SH-14, SI-08). */
    fun findForums()

    /** **By address** (SH-14, SI-01). */
    fun addByAddress()

    /** Single-forum mode's **Sign in with idejo ID** (MD-03). */
    fun idejoId()
}

/**
 * The logo menu (SH-10 to SH-15): **This forum** with its logo, and its name and address,
 * which open the home page on **Categories** ([onHome]); then on one line **Followed
 * topics** (the sidebar, while it is not docked, SH-30), **Invite** (the forum's link) and
 * **Support** (greyed, not offered yet). In idejo ID mode ([group]),
 * **Your forums** (SH-12), the inbox's shortcuts (SH-13) and **Add a forum** by address or from
 * the directory (SH-14); in single-forum mode, **Add a forum** explains that the app holds this
 * forum only and offers idejo ID mode (MD-03). It is the content of [MenuPanel], which
 * scrolls it when it is taller than the screen.
 */
@Composable
fun LogoMenu(
    name: String,
    address: String,
    onHome: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    logo: String? = null,
    group: GroupMenu? = null,
    actions: LogoMenuActions? = null,
    onSidebar: (() -> Unit)? = null,
    chatService: Boolean = false,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
        Heading(stringResource(R.string.logo_menu_this_forum))
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ForumLogo(name, size = 64.dp, logoUrl = logo)
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onHome),
            ) {
                Text(name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ForumButtons(
            listOfNotNull(
                onSidebar?.let {
                    ForumButton(DesignR.drawable.ic_menu, R.string.logo_menu_followed, it)
                },
                ForumButton(R.drawable.ic_share, R.string.logo_menu_invite, onShare),
                // Not offered yet: shown greyed.
                ForumButton(R.drawable.ic_help, R.string.logo_menu_support, null),
            ),
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        if (group != null && actions != null) {
            GroupPart(group, actions)
        } else {
            SingleForum(name, actions, chatService)
        }
    }
}

/**
 * The forum menu over the group's inbox (SH-10, steward request 2026-11-23): no **This
 * forum**, as the inbox is none, but **Support** (greyed, not offered yet) on its line, then
 * **Your forums**, the inbox's shortcuts and **Add a forum** as in a forum (SH-12 to SH-14).
 */
@Composable
internal fun InboxMenu(
    group: GroupMenu,
    actions: LogoMenuActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 8.dp)) {
        ForumButtons(listOf(ForumButton(R.drawable.ic_help, R.string.logo_menu_support, null)))
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        GroupPart(group, actions)
    }
}

/** SH-12 to SH-14, in idejo ID mode. */
@Composable
private fun ColumnScope.GroupPart(
    group: GroupMenu,
    actions: LogoMenuActions,
) {
    Heading(stringResource(R.string.logo_menu_your_forums))
    YourForumsList(group.forums, actions)
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Item(
        R.drawable.ic_notifications,
        stringResource(R.string.logo_menu_all_topics),
        actions::topics,
    )
    Item(
        R.drawable.ic_chat,
        stringResource(R.string.place_conversations),
        actions::chats,
        count = group.chatsWithNews?.takeIf { it > 0 },
    )
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Heading(stringResource(R.string.logo_menu_add_forum))
    Item(R.drawable.ic_add, stringResource(R.string.logo_menu_by_address), actions::addByAddress)
    if (group.directory) {
        Item(
            R.drawable.ic_search,
            stringResource(R.string.logo_menu_find_more),
            actions::findForums,
        )
    }
}

/** MD-03: the app holds this forum only; idejo ID mode would keep it beside the others. */
@Composable
private fun SingleForum(
    name: String,
    actions: LogoMenuActions?,
    chatService: Boolean,
) = Column {
    // Private conversations live on the group's inbox, with an idejo ID (steward decision,
    // 2026-11-20): greyed here, with Connect; none on a forum without a chat service.
    if (chatService && actions != null) {
        UnavailableItem(
            R.drawable.ic_chat,
            stringResource(R.string.place_conversations),
            stringResource(R.string.logo_menu_connect),
            stringResource(R.string.logo_menu_connect_conversations),
            actions::idejoId,
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
    }
    Heading(stringResource(R.string.logo_menu_add_forum))
    Text(
        stringResource(R.string.logo_menu_single_forum, name),
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyLarge,
    )
    if (actions != null) {
        Text(
            stringResource(R.string.logo_menu_offer_idejo_id, name),
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedButton(
            onClick = actions::idejoId,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) { Text(stringResource(R.string.logo_menu_sign_in_idejo_id)) }
    }
}

/** One of **This forum**'s buttons; without [onClick], greyed and unavailable. */
internal data class ForumButton(
    val icon: Int,
    val label: Int,
    val onClick: (() -> Unit)?,
)

/**
 * [buttons] on one line, each its icon and label; when the labels do not all fit, the
 * leftmost lose theirs first (TalkBack still reads them).
 */
@Composable
private fun ForumButtons(buttons: List<ForumButton>) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val labels = buttons.map { stringResource(it.label) }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        val density = LocalDensity.current
        val bare = with(density) { (BUTTON_PADDING * 2 + ICON).roundToPx() }
        val extra =
            labels.map {
                measurer.measure(it, style).size.width + with(density) { GAP.roundToPx() }
            }
        val labelled = labelled(bare, extra, constraints.maxWidth)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            buttons.forEachIndexed { index, button ->
                ForumButtonView(button, labels[index], index >= buttons.size - labelled)
            }
        }
    }
}

/**
 * How many buttons, from the right, keep their label in [room]: each is [bare] without it,
 * and its [extra] wider with it.
 */
internal fun labelled(
    bare: Int,
    extra: List<Int>,
    room: Int,
): Int =
    (extra.size downTo 0).first { kept ->
        bare * extra.size + extra.takeLast(kept).sum() <=
            room
    }

@Composable
private fun ForumButtonView(
    button: ForumButton,
    label: String,
    labelled: Boolean,
) {
    val enabled = button.onClick != null
    val colour =
        if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
        }
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button) { button.onClick?.invoke() }
            .semantics(mergeDescendants = true) {
                if (!labelled) contentDescription = label
                if (!enabled) disabled()
            }.heightIn(min = 48.dp)
            .padding(horizontal = BUTTON_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Icon(painterResource(button.icon), null, Modifier.size(ICON), tint = colour)
        if (labelled) Text(label, style = MaterialTheme.typography.labelLarge, color = colour)
    }
}

private val BUTTON_PADDING = 12.dp
private val ICON = 24.dp
private val GAP = 8.dp
