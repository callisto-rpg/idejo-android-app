// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.dialog
import org.idejo.app.core.data.Access
import org.idejo.app.core.data.ForumSession
import org.idejo.app.feature.composer.ForwardRoute
import org.idejo.app.feature.composer.ShareViewModel
import org.idejo.app.feature.shell.Place
import org.idejo.app.feature.shell.ShareDialog

/**
 * The share sheet's card (CO-14): the topic chosen opens with the pictures in its composer;
 * closed without a choice, they are dropped.
 */
internal fun NavGraphBuilder.shareCard(navigation: NavHostController) =
    dialog<ShareDialog> {
        val share: ShareViewModel = hiltViewModel()
        var picked by remember { mutableStateOf(false) }
        DisposableEffect(Unit) { onDispose { if (!picked) share.drop() } }
        ForwardRoute(
            onPick = { target ->
                picked = true
                navigation.popBackStack()
                navigation.navigate(Place.Topic(target.id, share = true)) { launchSingleTop = true }
            },
            title = org.idejo.app.feature.composer.R.string.share_title,
        )
    }

/** Pictures shared to the app open the share card, once signed in (CO-14). */
@Composable
internal fun SharedImagesCard(
    session: ForumSession,
    navigation: NavHostController,
    share: ShareViewModel = hiltViewModel(),
) {
    val pending by share.pending.collectAsStateWithLifecycle()
    val access by session.access.collectAsStateWithLifecycle()
    LaunchedEffect(pending, access) {
        if (pending.isNotEmpty() && access == Access.SignedIn) navigation.navigate(ShareDialog)
    }
}
