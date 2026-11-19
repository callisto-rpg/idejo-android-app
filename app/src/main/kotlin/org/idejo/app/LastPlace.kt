// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import dagger.hilt.android.lifecycle.HiltViewModel
import org.idejo.app.core.data.LastPlaces
import org.idejo.app.core.data.PendingLinks
import org.idejo.app.feature.shell.Place
import org.idejo.app.feature.shell.placeOf
import org.idejo.app.feature.shell.routeOf
import javax.inject.Inject

@HiltViewModel
class LastPlaceViewModel
    @Inject
    constructor(
        val places: LastPlaces,
        val links: PendingLinks,
    ) : ViewModel()

/**
 * The last place opened (MD-05) in the forum or inbox at [origin]: each page shown is kept,
 * and on the launch that restored this session its last page opens again, unless a tapped
 * notification asks for another. Returns what keeps a topic's first message on screen.
 */
@Composable
internal fun rememberLastPlace(
    origin: String,
    navigation: NavHostController,
    viewModel: LastPlaceViewModel = hiltViewModel(key = "last:$origin"),
): (Place.Topic) -> Unit {
    LaunchedEffect(origin) {
        val route = viewModel.places.atLaunch(origin) ?: return@LaunchedEffect
        if (viewModel.links.pending.value
                ?.origin == origin
        ) {
            return@LaunchedEffect
        }
        placeOf(route)?.let { navigation.go(it) }
    }
    val entry by navigation.currentBackStackEntryAsState()
    LaunchedEffect(entry) {
        val place = entry?.place() ?: return@LaunchedEffect
        if (place in PASSING || place is Place.NewTopic) return@LaunchedEffect
        viewModel.places.keep(origin, routeOf(place))
    }
    return { topic -> viewModel.places.keep(origin, routeOf(topic)) }
}

/** Sign-in pages and the new topic's composer: never where the app opens. */
private val PASSING = setOf<Place>(Place.IdejoId, Place.AddForum)
