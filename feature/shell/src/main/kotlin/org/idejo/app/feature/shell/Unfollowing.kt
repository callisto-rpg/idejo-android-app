// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.idejo.app.core.data.SidebarState
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.model.Topic

/** A followed topic whose × was tapped: its notice shows, then fades ([shown]). */
@Stable
internal class Leaving(
    val topic: Topic,
) {
    /** How much of the notice shows, 1 until it fades to 0. */
    val shown = Animatable(1f)
}

/**
 * The sidebar's × (SH-33, steward request 2026-11-23): the topic's title gives way to "Topic
 * unfollowed. Tap to cancel" for [NOTICE_FOR], which then fades out; only then is the topic
 * unfollowed and the rows below slide up into its place. A tap on the notice cancels it, and
 * nothing is sent.
 */
@Stable
internal class Unfollowing {
    /** The notices showing, by topic id. */
    val waiting = mutableStateMapOf<String, Leaving>()

    /** Unfollowed here: hidden until the forum's list drops them. */
    var gone by mutableStateOf(emptySet<String>())

    fun start(topic: Topic) {
        waiting[topic.id] = Leaving(topic)
    }

    fun cancel(id: String) {
        waiting.remove(id)
    }
}

/**
 * Times [unfollowing]'s notices and unfollows each with [actions] once faded. A sidebar
 * closed while notices show unfollows their topics at once: the member did not cancel.
 */
@Composable
internal fun UnfollowTimers(
    unfollowing: Unfollowing,
    state: SidebarState,
    actions: SidebarActions,
) {
    val current by rememberUpdatedState(actions)
    val reduced = IdejoTheme.reducedMotion
    unfollowing.waiting.keys.toList().forEach { id ->
        key(id) {
            LaunchedEffect(id) {
                val leaving = unfollowing.waiting[id] ?: return@LaunchedEffect
                delay(NOTICE_FOR)
                if (reduced) {
                    leaving.shown.snapTo(0f)
                } else {
                    leaving.shown.animateTo(0f, tween(FADE_MILLIS))
                }
                unfollowing.waiting.remove(id)
                unfollowing.gone += id
                current.follow(leaving.topic, false)
            }
        }
    }
    // Once the forum's list no longer has a topic, it need not be hidden here.
    LaunchedEffect(state.sections) {
        val listed =
            state.sections
                .flatMap { it.categories }
                .flatMap { it.topics }
                .map { it.id }
                .toSet()
        unfollowing.gone = unfollowing.gone.filter { it in listed }.toSet()
    }
    DisposableEffect(unfollowing) {
        onDispose {
            unfollowing.waiting.values.forEach { current.follow(it.topic, false) }
            unfollowing.waiting.clear()
        }
    }
}

/** The notice in place of the topic's title, at 70 % and fading with [leaving]. */
@Composable
internal fun UnfollowedNotice(
    leaving: Leaving,
    modifier: Modifier = Modifier,
) {
    Text(
        stringResource(R.string.sidebar_unfollowed),
        modifier.graphicsLayer { alpha = NOTICE_ALPHA * leaving.shown.value },
        style = IdejoTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A sidebar row that slides into the place of one gone above it (none with reduced motion). */
internal fun Modifier.sliding(
    item: LazyItemScope,
    motion: Boolean,
): Modifier =
    if (motion) {
        with(item) {
            then(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null))
        }
    } else {
        this
    }

/**
 * A sidebar topic's × ([onUnfollow]) when followed, or the open topic's + ([onFollow]) when
 * not, in a [modifier]-sized target.
 */
@Composable
internal fun FollowButton(
    topic: Topic,
    followed: Boolean,
    open: Boolean,
    offline: Boolean,
    onUnfollow: () -> Unit,
    onFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (followed) {
        IconButton(onClick = onUnfollow, modifier, enabled = !offline) {
            // 40 % smaller than an icon's 24 dp, in the same target (steward, 2026-11-23).
            Icon(
                painterResource(org.idejo.app.core.designsystem.R.drawable.ic_close),
                stringResource(R.string.sidebar_unfollow, topic.title),
                Modifier.size(CLOSE_ICON),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else if (open) {
        IconButton(onClick = onFollow, modifier, enabled = !offline) {
            Icon(
                painterResource(R.drawable.ic_add),
                stringResource(R.string.sidebar_follow, topic.title),
            )
        }
    }
}

/** The ×: 40 % smaller than an icon's 24 dp. */
private val CLOSE_ICON = 14.4.dp

/** How long the notice shows before it fades. */
private const val NOTICE_FOR = 3_000L
private const val FADE_MILLIS = 300
private const val NOTICE_ALPHA = 0.7f
