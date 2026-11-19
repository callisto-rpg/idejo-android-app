// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.idejo.app.core.designsystem.component.Avatar
import org.idejo.app.core.designsystem.component.Crumb
import org.idejo.app.core.designsystem.component.TopBar
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.designsystem.theme.parseHexColor
import org.idejo.app.core.designsystem.theme.pictureUrl

/** The inbox's three tabs (IB-01). */
enum class InboxTab(
    val title: Int,
) {
    Forums(R.string.inbox_tab_forums),
    Topics(R.string.inbox_tab_topics),
    Chats(R.string.inbox_tab_chats),
}

/**
 * The inbox (IB-01): its logo, the breadcrumb "› Forums", "› Topics" or "› Chats", the
 * member's picture; the three tabs under it, swiped between.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    state: InboxState,
    forums: @Composable () -> Unit,
    topics: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: InboxTab = InboxTab.Forums,
) {
    val pager = rememberPagerState(initialPage = initialTab.ordinal) { InboxTab.entries.size }
    val scope = rememberCoroutineScope()
    val tab = InboxTab.entries[pager.currentPage]
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            ),
        ) {
            TopBar(
                crumbs =
                    listOf(
                        Crumb.Forum(state.name, onClick = {}, logo = logoOf(state.logoUrl)),
                        Crumb.Page(stringResource(tab.title)),
                    ),
            ) { Me(state) }
            PrimaryTabRow(selectedTabIndex = pager.currentPage) {
                InboxTab.entries.forEach { each ->
                    Tab(
                        selected = each == tab,
                        onClick = { scope.launch { pager.animateScrollToPage(each.ordinal) } },
                        text = { Text(stringResource(each.title)) },
                    )
                }
            }
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                when (InboxTab.entries[page]) {
                    InboxTab.Forums -> forums()
                    InboxTab.Topics -> topics()
                    InboxTab.Chats -> Chats()
                }
            }
        }
    }
}

/** IB-06 brings the inbox's conversations here. */
@Composable
private fun Chats() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.inbox_chats_soon),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Me(state: InboxState) {
    val me = state.me ?: return
    val url = pictureUrl(me.avatarUrl, me.avatarDarkUrl, IdejoTheme.dark)
    Avatar(
        name = me.displayName,
        color = parseHexColor(me.colour) ?: MaterialTheme.colorScheme.tertiary,
        size = 32.dp,
        contentDescription = me.displayName,
        image = url?.let { { AsyncImage(it, null, Modifier.fillMaxSize()) } },
    )
}

private fun logoOf(url: String?): (@Composable () -> Unit)? =
    url?.let { { AsyncImage(it, null, Modifier.fillMaxSize()) } }
