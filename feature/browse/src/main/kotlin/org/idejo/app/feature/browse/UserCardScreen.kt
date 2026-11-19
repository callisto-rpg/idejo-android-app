// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.idejo.app.core.designsystem.component.Avatar
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.designsystem.theme.parseHexColor
import org.idejo.app.core.designsystem.theme.pictureUrl
import org.idejo.app.core.markdown.IdejoMarkdown
import org.idejo.app.core.markdown.MarkdownActions
import org.idejo.app.core.markdown.MarkdownBody
import org.idejo.app.core.markdown.plainText
import org.idejo.app.core.model.UserCard

/** A user card (BR-09) in its dialog; the route's `id` names the user. */
@Composable
fun UserCardRoute(
    onClose: () -> Unit,
    onConversation: (String) -> Unit,
    viewModel: UserCardViewModel = hiltViewModel(),
    onLink: (String) -> Unit = {},
    /** **Sign in with idejo ID** (PC-02), from single-forum mode. */
    onIdejoId: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val opened by rememberUpdatedState(onConversation)
    val link by rememberUpdatedState(onLink)
    LaunchedEffect(state.conversation) { state.conversation?.let { opened(it) } }
    LaunchedEffect(state.outside) {
        state.outside?.let {
            link(it)
            viewModel.outsideOpened()
        }
    }
    UserCardScreen(
        state,
        onIgnore = viewModel::ignore,
        onRetry = viewModel::load,
        onClose = onClose,
        onMessage = viewModel::message,
        onIdejoId = onIdejoId,
    )
}

/**
 * The web's user card: picture, name and user tag, @username, roles, the bio (its first 200
 * characters as text when longer), and **Ignore** or **Stop ignoring** when the server lets
 * the member ignore them, and **Private Message** when they take private messages from the
 * member (PC-03).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserCardScreen(
    state: UserCardUi,
    onIgnore: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onMessage: () -> Unit = {},
    onIdejoId: () -> Unit = {},
) {
    Surface(
        modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val card = state.card
            when {
                card != null -> Card(card, state, onIgnore, onMessage, onIdejoId)
                state.loading -> MoreSpinner()
                else -> state.problem?.let { ProblemRow(it, onRetry) }
            }
            TextButton(onClick = onClose, Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.user_close))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Card(
    card: UserCard,
    state: UserCardUi,
    onIgnore: (Boolean) -> Unit,
    onMessage: () -> Unit,
    onIdejoId: () -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Head(card, state.origin)
    if (card.roles.isNotEmpty()) Roles(card)
    card.bioMd?.takeIf { it.isNotBlank() }?.let { Bio(it) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.mayMessage) {
            Button(onClick = onMessage, enabled = !state.busy) {
                Text(stringResource(R.string.user_private_message))
            }
        }
        card.ignoredByMe?.let { ignoring ->
            OutlinedButton(onClick = { onIgnore(!ignoring) }, enabled = !state.busy) {
                Text(stringResource(if (ignoring) R.string.user_unignore else R.string.user_ignore))
            }
        }
    }
    if (state.offersIdejoId) OfferIdejoId(onIdejoId)
    state.problem?.let { problem ->
        ProblemRow(problem, onRetry = { onIgnore(card.ignoredByMe != true) })
    }
}

/** PC-02: the chat opens on the web; in idejo ID mode it would be the inbox's. */
@Composable
private fun OfferIdejoId(onIdejoId: () -> Unit) =
    Column {
        Text(
            stringResource(R.string.user_chat_outside),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onIdejoId) { Text(stringResource(R.string.user_chat_idejo_id)) }
    }

/** Picture, name and user tag, @username. */
@Composable
private fun Head(
    card: UserCard,
    origin: String,
) {
    val picture = remember(origin) { pictures(origin) }
    val url = pictureUrl(card.avatarUrl, card.avatarDarkUrl, IdejoTheme.dark)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Avatar(
            card.displayName,
            parseHexColor(card.colour) ?: MaterialTheme.colorScheme.tertiary,
            size = 64.dp,
            contentDescription = stringResource(R.string.user_picture, card.displayName),
            image = url?.let { { picture(it, Modifier) } },
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    card.displayName,
                    Modifier.semantics { heading() },
                    style = IdejoTheme.typography.titleLarge,
                )
                card.userTag?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = IdejoTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            card.username?.let {
                Text(
                    "@$it",
                    style = IdejoTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The member's roles, names in their colours. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Roles(card: UserCard) {
    val roles = stringResource(R.string.user_roles)
    FlowRow(
        Modifier.semantics { contentDescription = roles },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        card.roles.forEach { role ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    role.name,
                    Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = IdejoTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = IdejoTheme.colors.roleName(role.colour),
                )
            }
        }
    }
}

/** The bio, whole when short; its first [BIO_LIMIT] characters as text when longer (BR-09). */
@Composable
private fun Bio(markdown: String) {
    val document = remember(markdown) { IdejoMarkdown.parse(markdown) }
    val spoiler = stringResource(org.idejo.app.core.markdown.R.string.markdown_spoiler)
    val plain = remember(document, spoiler) { document.plainText(spoiler) }
    if (plain.length > BIO_LIMIT) {
        Text(plain.take(BIO_LIMIT).trimEnd() + "…", style = IdejoTheme.typography.bodyMedium)
    } else {
        var revealed by remember { mutableStateOf(emptySet<Int>()) }
        MarkdownBody(
            document,
            actions = MarkdownActions(onReveal = { revealed = revealed + it }),
            revealed = revealed,
            style = IdejoTheme.typography.bodyMedium,
        )
    }
}

private const val BIO_LIMIT = 200
