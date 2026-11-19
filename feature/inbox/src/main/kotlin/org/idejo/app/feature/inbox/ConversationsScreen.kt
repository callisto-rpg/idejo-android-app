// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.data.ConversationsState
import org.idejo.app.core.designsystem.component.Avatar
import org.idejo.app.core.designsystem.component.relativeTime
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.designsystem.theme.parseHexColor
import org.idejo.app.core.designsystem.theme.pictureUrl
import org.idejo.app.core.model.Conversation

@Composable
fun ConversationsRoute(
    onConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
    titled: Boolean = true,
    viewModel: ConversationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ConversationsScreen(
        state,
        viewModel.origin,
        onConversation,
        viewModel::refresh,
        modifier,
        titled,
    )
}

/**
 * **Private Conversations** (PC-01): the member's as cards, the latest active first, each
 * with the other person, the latest message and how many are new, as the web lists them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    state: ConversationsState,
    origin: String,
    onConversation: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    /** False under the inbox's **Chats** tab, which names the page (IB-06). */
    titled: Boolean = true,
) {
    PullToRefreshBox(
        state.loading && state.conversations.isNotEmpty(),
        onRefresh,
        modifier.fillMaxSize(),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (titled) {
                item(key = "title") {
                    Text(
                        stringResource(R.string.conversations_title),
                        Modifier.semantics { heading() },
                        style = IdejoTheme.typography.headlineSmall,
                    )
                }
            }
            items(state.conversations, key = { it.id }) { conversation ->
                ConversationCard(conversation, origin) { onConversation(conversation.id) }
            }
            item(key = "end") { End(state, onRefresh) }
        }
    }
}

@Composable
private fun End(
    state: ConversationsState,
    onRetry: () -> Unit,
) {
    val problem = state.problem
    when {
        problem != null -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(problemText(problem), Modifier.weight(1f))
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.conversations_try_again))
                }
            }
        }

        state.loading && state.conversations.isEmpty() -> {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        state.conversations.isEmpty() -> {
            Text(
                stringResource(R.string.conversations_none),
                style = IdejoTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun problemText(problem: ApiProblem): String =
    when (problem) {
        is ApiProblem.Server -> problem.error.message
        is ApiProblem.Network -> stringResource(R.string.conversations_offline)
        is ApiProblem.Malformed -> stringResource(R.string.conversations_could_not_load)
    }

@Composable
private fun ConversationCard(
    conversation: Conversation,
    origin: String,
    onClick: () -> Unit,
) {
    val news = conversation.unreadCount > 0
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Picture(conversation, origin)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        conversation.name,
                        Modifier.weight(1f),
                        style = IdejoTheme.typography.titleMedium,
                        fontWeight = if (news) FontWeight.SemiBold else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        relativeTime(conversation.lastActivityAt),
                        style = IdejoTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                conversation.lastMessage?.let { last ->
                    Text(
                        stringResource(
                            R.string.conversations_last,
                            last.authorName.orEmpty(),
                            last.excerpt.orEmpty(),
                        ),
                        style = IdejoTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (news) NewCount(conversation.unreadCount)
        }
    }
}

@Composable
private fun Picture(
    conversation: Conversation,
    origin: String,
) {
    val person = conversation.with.firstOrNull()
    val url = person?.let { pictureUrl(it.avatarUrl, it.avatarDarkUrl, IdejoTheme.dark) }
    Avatar(
        conversation.name,
        parseHexColor(person?.colour) ?: MaterialTheme.colorScheme.tertiary,
        size = 40.dp,
        image =
            url?.let {
                {
                    AsyncImage(
                        if (it.startsWith("/")) origin.trimEnd('/') + it else it,
                        null,
                        contentScale = ContentScale.Crop,
                    )
                }
            },
    )
}

@Composable
private fun NewCount(count: Int) =
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
        Text(
            pluralStringResource(R.plurals.conversations_new, count, count),
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = IdejoTheme.typography.labelLarge,
        )
    }
