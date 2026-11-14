// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.data.TopicState
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Tag
import org.idejo.app.core.model.Topic

/** Title, tags, state and **Follow** / **Unfollow** (TV-10). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Header(
    topic: Topic,
    actions: TopicActions,
) = Column(
    Modifier.fillMaxWidth().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
) {
    Text(topic.title, Modifier.semantics { heading() }, style = IdejoTheme.typography.headlineSmall)
    if (topic.tags.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            topic.tags.forEach { tag -> TagChip(tag, actions.onTag) }
        }
    }
    val state =
        when {
            topic.archived -> R.string.topic_archived
            topic.locked -> R.string.topic_locked
            else -> null
        }
    state?.let {
        Text(
            stringResource(it),
            style = IdejoTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (topic.followedByEveryone) {
            Text(
                stringResource(R.string.topic_followed_by_everyone),
                style = IdejoTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (topic.followed) {
            OutlinedButton(onClick = { actions.onFollow(false) }) {
                Text(stringResource(R.string.topic_unfollow))
            }
        } else {
            Button(
                onClick = { actions.onFollow(true) },
            ) { Text(stringResource(R.string.topic_follow)) }
        }
    }
}

@Composable
private fun TagChip(
    tag: Tag,
    onTag: (String) -> Unit,
) {
    val slug = tag.slug
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = if (slug != null) Modifier.clickable { onTag(slug) } else Modifier,
    ) {
        Text(
            tag.name,
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = IdejoTheme.typography.labelMedium,
        )
    }
}

/** The red **N unread messages** line where the member left off (TV-01). */
@Composable
internal fun UnreadLine(count: Int) =
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
        Text(
            pluralStringResource(R.plurals.topic_unread, count, count),
            style = IdejoTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
    }

/** **N earlier messages** / **N later messages** (TV-05). */
@Composable
internal fun More(
    count: Int,
    earlier: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) = Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
    if (loading) {
        CircularProgressIndicator()
    } else {
        TextButton(onClick = onClick) {
            Text(
                when {
                    count == 0 && earlier -> stringResource(R.string.topic_earlier_some)
                    count == 0 -> stringResource(R.string.topic_later_some)
                    earlier -> pluralStringResource(R.plurals.topic_earlier, count, count)
                    else -> pluralStringResource(R.plurals.topic_later, count, count)
                },
            )
        }
    }
}

/** **N new messages**, or **Jump to the end to write** (TV-05). */
@Composable
internal fun Floating(
    newBelow: Int,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) = Button(onClick = onEnd, modifier.padding(16.dp)) {
    Text(
        if (newBelow > 0) {
            pluralStringResource(R.plurals.topic_new, newBelow, newBelow)
        } else {
            stringResource(R.string.topic_jump_end)
        },
    )
}

/** Why the last action failed, over the page, with **Try again** (it reads the page again). */
@Composable
internal fun ProblemBar(
    problem: ApiProblem,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) = Surface(
    modifier.padding(16.dp),
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
) {
    Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(problemText(problem), Modifier.weight(1f, fill = false))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.topic_retry)) }
    }
}

@Composable
private fun problemText(problem: ApiProblem): String =
    when (problem) {
        is ApiProblem.Server -> problem.error.message
        is ApiProblem.Network -> stringResource(R.string.topic_offline)
        is ApiProblem.Malformed -> stringResource(R.string.topic_could_not_load)
    }

@Composable
internal fun Gone() =
    Text(
        stringResource(R.string.topic_gone),
        Modifier.padding(24.dp),
        style = IdejoTheme.typography.bodyLarge,
    )

@Composable
internal fun Spinner() =
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

@Composable
internal fun Problem(
    problem: ApiProblem,
    onRetry: () -> Unit,
) = Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(problemText(problem), style = IdejoTheme.typography.bodyLarge)
    TextButton(onClick = onRetry) { Text(stringResource(R.string.topic_retry)) }
}
