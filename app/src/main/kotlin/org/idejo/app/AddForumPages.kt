// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import org.idejo.app.feature.onboarding.AddForumRoute
import org.idejo.app.feature.onboarding.IdejoIdRoute
import org.idejo.app.feature.shell.Place

/** The logo menu's **Add a forum** (SH-14) and **Sign in with idejo ID** (MD-03, PC-02). */
internal fun NavGraphBuilder.addForumPages(navigation: NavHostController) {
    composable<Place.IdejoId> { IdejoIdRoute(onBack = { navigation.popBackStack() }) }
    composable<Place.AddForum> { AddForumRoute(onBack = { navigation.popBackStack() }) }
}
