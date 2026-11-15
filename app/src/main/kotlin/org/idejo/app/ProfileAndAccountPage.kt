// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import org.idejo.app.feature.settings.ProfileAndAccountRoute
import org.idejo.app.feature.shell.Place

/** **Profile and account** (ST): the overview, or one section by its key. */
internal fun NavGraphBuilder.profileAndAccountPage(
    navigation: NavHostController,
    page: @Composable (@Composable () -> Unit) -> Unit,
) = composable<Place.ProfileAndAccount> { entry ->
    page {
        ProfileAndAccountRoute(
            section = entry.toRoute<Place.ProfileAndAccount>().section,
            onOpenSection = { navigation.go(Place.ProfileAndAccount(it)) },
        )
    }
}
