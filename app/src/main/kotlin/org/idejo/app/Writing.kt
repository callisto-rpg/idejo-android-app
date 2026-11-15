// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.idejo.app.feature.composer.ComposerRoute
import org.idejo.app.feature.composer.ComposerViewModel
import org.idejo.app.feature.topic.TopicWriting

/**
 * The composer the topic page places (CO-01): features never depend on each other, so the
 * app hands the page the composer of the same back-stack entry.
 */
@Composable
internal fun rememberTopicWriting(viewModel: ComposerViewModel = hiltViewModel()): TopicWriting {
    val composer = viewModel.composer ?: return TopicWriting()
    val state by composer.state.collectAsStateWithLifecycle()
    val me by viewModel.me.collectAsStateWithLifecycle()
    val bubbles by viewModel.bubbles.collectAsStateWithLifecycle()
    return TopicWriting(
        openMessageId = state.openMessageId,
        mayWrite = state.server?.mayPost == true,
        onComplete = composer::complete,
        box = { modifier -> ComposerRoute(composer, me, viewModel.origin, bubbles, modifier) },
    )
}
