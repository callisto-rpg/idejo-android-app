// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

/** idejo ID mode (MD-02): Back on the forum's first page leads to the inbox. */
@Composable
internal fun BackToInbox(
    navigation: NavHostController,
    onBackToInbox: (() -> Unit)?,
) {
    val entry by navigation.currentBackStackEntryAsState()
    val first = entry != null && navigation.previousBackStackEntry == null
    BackHandler(onBackToInbox != null && first) { onBackToInbox?.invoke() }
}
