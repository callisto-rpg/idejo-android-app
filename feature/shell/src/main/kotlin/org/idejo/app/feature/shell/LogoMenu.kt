// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
 * The logo menu (SH-10 to SH-15): **This forum** with its logo, name and address, **Home**,
 * **Sidebar** while it is not docked (SH-30) and **Share link**. In idejo ID mode ([group]),
 * **Your forums** (SH-12), the inbox's shortcuts (SH-13) and **Add a forum** by address or from
 * the directory (SH-14); in single-forum mode, **Add a forum** explains that the app holds this
 * forum only and offers idejo ID mode (MD-03).
 */
@Composable
fun LogoMenu(
    name: String,
    address: String,
    onHome: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    logo: String? = null,
    group: GroupMenu? = null,
    actions: LogoMenuActions? = null,
    onSidebar: (() -> Unit)? = null,
) {
    MenuPage(onClose, modifier) {
        Heading(stringResource(R.string.logo_menu_this_forum))
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ForumLogo(name, size = 64.dp, logoUrl = logo)
            Column(Modifier.weight(1f)) {
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
        Item(R.drawable.ic_home, stringResource(R.string.logo_menu_home), onHome)
        onSidebar?.let {
            Item(DesignR.drawable.ic_menu, stringResource(R.string.sidebar_title), it)
        }
        Item(R.drawable.ic_share, stringResource(R.string.logo_menu_share), onShare)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        if (group != null && actions != null) {
            GroupPart(group, actions)
        } else {
            SingleForum(name, actions)
        }
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
) = Column {
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
