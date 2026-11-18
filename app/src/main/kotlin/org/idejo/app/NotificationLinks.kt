// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dagger.hilt.android.lifecycle.HiltViewModel
import org.idejo.app.core.data.ForumSession
import org.idejo.app.core.data.LinkTarget
import org.idejo.app.core.data.PendingLinks
import org.idejo.app.core.data.linkTarget
import org.idejo.app.feature.shell.Place
import javax.inject.Inject

/** The address of a notification tapped, waiting for its forum (NT-04). */
@HiltViewModel
class PendingLinksViewModel
    @Inject
    constructor(
        val links: PendingLinks,
    ) : ViewModel()

/** A notification tapped opens its page here, once its forum is the one on screen (NT-04). */
@Composable
internal fun NotificationLinks(
    session: ForumSession,
    navigation: NavHostController,
    viewModel: PendingLinksViewModel = hiltViewModel(),
) {
    val pending by viewModel.links.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pending, session) {
        val link = viewModel.links.take(session.origin) ?: return@LaunchedEffect
        navigation.go(linkTarget(session, link.url).place)
    }
}

private val LinkTarget.place: Place
    get() =
        when (this) {
            LinkTarget.Home -> Place.Home
            LinkTarget.Unread -> Place.Unread
            LinkTarget.Conversations -> Place.Conversations
            is LinkTarget.Topic -> Place.Topic(id, messageId, private = private)
        }
