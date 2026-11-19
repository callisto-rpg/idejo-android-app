// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import org.idejo.app.feature.onboarding.AddForumRoute
import org.idejo.app.feature.onboarding.IdejoIdRoute
import org.idejo.app.feature.settings.AppSettingsRoute
import org.idejo.app.feature.shell.Place

/**
 * App settings (ST-20 to ST-23), whose **Mode** leads to idejo ID's sign-in (MD-04); the logo
 * menu's **Add a forum** (SH-14) and **Sign in with idejo ID** (MD-03, PC-02).
 */
internal fun NavGraphBuilder.addForumPages(navigation: NavHostController) {
    composable<Place.AppSettings> {
        AppSettingsRoute(Modifier.fillMaxSize(), onIdejoId = { navigation.go(Place.IdejoId) })
    }
    composable<Place.IdejoId> { IdejoIdRoute(onBack = { navigation.popBackStack() }) }
    composable<Place.AddForum> { AddForumRoute(onBack = { navigation.popBackStack() }) }
}
