// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** idejo ID mode's inbox (MD-02): the group's forums (SI-06, SI-07), and the directory (SI-08). */
@Composable
fun InboxRoute(modifier: Modifier = Modifier) {
    var finding by rememberSaveable { mutableStateOf(false) }
    if (finding) {
        BackHandler { finding = false }
        FindForumsRoute(onBack = { finding = false }, modifier = modifier)
    } else {
        ConnectForumsRoute(onFindMore = { finding = true }, modifier = modifier)
    }
}
