// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.dialog
import org.idejo.app.core.data.Access
import org.idejo.app.core.data.ForumSession
import org.idejo.app.feature.composer.ForwardRoute
import org.idejo.app.feature.composer.ShareForum
import org.idejo.app.feature.composer.ShareViewModel
import org.idejo.app.feature.shell.Place
import org.idejo.app.feature.shell.ShareDialog

/**
 * The share sheet's card (CO-14, DL-04): the topic chosen opens with the pictures or text
 * in its composer; with several forums held, another can be picked first, and the card
 * opens again there. Closed without a choice, what was shared is dropped.
 */
internal fun NavGraphBuilder.shareCard(navigation: NavHostController) =
    dialog<ShareDialog> {
        val share: ShareViewModel = hiltViewModel()
        var picked by remember { mutableStateOf(false) }
        DisposableEffect(Unit) { onDispose { if (!picked) share.drop() } }
        val forums by share.forums.collectAsStateWithLifecycle()
        ForwardRoute(
            header = {
                ShareForums(forums, share.here) {
                    picked = true
                    share.forum(it)
                }
            },
            onPick = { target ->
                picked = true
                navigation.popBackStack()
                navigation.navigate(Place.Topic(target.id, share = true)) { launchSingleTop = true }
            },
            title = org.idejo.app.feature.composer.R.string.share_title,
        )
    }

/** The forums to share to (DL-04): the open one chosen; another opens it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareForums(
    forums: List<ShareForum>,
    here: String?,
    onForum: (String) -> Unit,
) {
    if (forums.isEmpty()) return
    FlowRow(
        Modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        forums.forEach { forum ->
            FilterChip(
                selected = forum.origin == here,
                onClick = { if (forum.origin != here) onForum(forum.origin) },
                label = { Text(forum.name) },
            )
        }
    }
}

/** Pictures or text shared to the app open the share card, once signed in (CO-14, DL-04). */
@Composable
internal fun SharedImagesCard(
    session: ForumSession,
    navigation: NavHostController,
    share: ShareViewModel = hiltViewModel(),
) {
    val pending by share.pending.collectAsStateWithLifecycle()
    val access by session.access.collectAsStateWithLifecycle()
    LaunchedEffect(pending, access) {
        if (pending && access == Access.SignedIn) navigation.navigate(ShareDialog)
    }
}
