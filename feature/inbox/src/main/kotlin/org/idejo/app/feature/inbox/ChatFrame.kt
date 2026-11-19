// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.idejo.app.core.designsystem.component.Crumb
import org.idejo.app.core.designsystem.component.TopBar

/**
 * A chat of the inbox (IB-06): the topic page of one of its private conversations, under the
 * breadcrumb "› Chats › name" (IB-01); the logo and **Chats** lead back to the tabs.
 */
@Composable
fun ChatFrameRoute(
    conversationId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationsViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChatFrame(
        inbox = viewModel.forumName,
        logoUrl = viewModel.logoUrl,
        title = state.named(conversationId)?.name,
        onBack = onBack,
        modifier = modifier,
        content = content,
    )
}

@Composable
fun ChatFrame(
    inbox: String,
    logoUrl: String?,
    title: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = InboxFrame(
    inbox,
    logoUrl,
    listOfNotNull(
        Crumb.Page(stringResource(R.string.inbox_tab_chats), onClick = onBack),
        title?.let { Crumb.Page(it) },
    ),
    onBack,
    modifier,
    content,
)

/**
 * Another page of the inbox: its **Profile and account** (ST-07), under the breadcrumb
 * "› Profile and account"; the logo leads back to the tabs.
 */
@Composable
fun InboxPageRoute(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConversationsViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) = InboxFrame(
    viewModel.forumName,
    viewModel.logoUrl,
    listOf(Crumb.Page(title)),
    onBack,
    modifier,
    content,
)

/** The inbox's top bar over a page: its logo (back to the tabs), then [pages]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxFrame(
    inbox: String,
    logoUrl: String?,
    pages: List<Crumb>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            ),
        ) {
            TopBar(
                crumbs =
                    listOf(
                        Crumb.Forum(
                            inbox,
                            onClick = onBack,
                            logo =
                                logoUrl?.let {
                                    { AsyncImage(it, null, Modifier.fillMaxSize()) }
                                },
                        ),
                    ) + pages,
            )
            Box(Modifier.weight(1f)) { content() }
        }
    }
}
