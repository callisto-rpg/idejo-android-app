// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

/** A library the app ships, and its licences by SPDX id (ST-23). */
@Immutable
data class Licence(
    val name: String,
    val version: String,
    val licences: List<String>,
)

/**
 * App settings' About (ST-23; steward decision, 2026-11-14): the app's version and licence,
 * its source code, the privacy note, and the licence of every library it ships, as the
 * build's licence check lists them.
 */
@Composable
fun AppSettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: AppSettingsViewModel = hiltViewModel(),
    /** **Mode** → idejo ID from single-forum mode: its sign-in (MD-04). */
    onIdejoId: () -> Unit = {},
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val licences by produceState(emptyList<Licence>()) {
        value = withContext(Dispatchers.IO) { context.shippedLicences() }
    }
    // A distributor installed meanwhile (NT-06) shows on returning; back from Sunup's store
    // page, push is enabled again.
    LifecycleResumeEffect(viewModel) {
        viewModel.distributors()
        if (viewModel.state.value.awaitingSunup) context.findActivity()?.let(viewModel::enablePush)
        onPauseOrDispose {}
    }
    val enablePush = rememberEnablePush(viewModel::enablePush)
    if (state.offerSunup) {
        SunupDialog { viewModel.onEvent(AppSettingsEvent.AnswerSunup(it)) }
    }
    val names = remember(state.distributors) { context.distributorNames(state.distributors) }
    AppSettingsScreen(
        context.versionName(),
        licences,
        onOpenSource = { context.openPage(SOURCE_URL) },
        modifier,
        state = state,
        distributorNames = names,
        onEvent = viewModel::onEvent,
        onIdejoId = onIdejoId,
        onEnablePush = enablePush,
    )
}

/** App settings: this device's choices (ST-20 to ST-22) when [state] has them, then About. */
@Composable
fun AppSettingsScreen(
    version: String,
    licences: List<Licence>,
    onOpenSource: () -> Unit,
    modifier: Modifier = Modifier,
    state: AppSettingsUiState? = null,
    distributorNames: Map<String, String> = emptyMap(),
    onEvent: (AppSettingsEvent) -> Unit = {},
    onIdejoId: () -> Unit = {},
    onEnablePush: () -> Unit = {},
) {
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp)) {
        state?.let { deviceSections(it, distributorNames, onEvent, onIdejoId, onEnablePush) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.about_app),
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(R.string.about_version, version),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    stringResource(R.string.about_licence),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onOpenSource,
                    Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(stringResource(R.string.about_source))
                }
            }
        }
        item { Heading(stringResource(R.string.about_privacy)) }
        item {
            Text(
                stringResource(R.string.about_privacy_note),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item { Heading(stringResource(R.string.about_bundled)) }
        items(BUNDLED) { Text(stringResource(it), Modifier.padding(vertical = 6.dp)) }
        item { Heading(stringResource(R.string.about_licences)) }
        items(licences) { licence -> Library(licence) }
    }
}

@Composable
private fun Library(licence: Licence) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(licence.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            listOf(licence.version, licence.licences.joinToString(", "))
                .filter { it.isNotEmpty() }
                .joinToString(" · ")
                .ifEmpty { stringResource(R.string.about_licence_unknown) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        Modifier.padding(top = 24.dp, bottom = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
    )
}

private val BUNDLED =
    listOf(
        R.string.about_bundled_fraunces,
        R.string.about_bundled_rubik,
        R.string.about_bundled_symbols,
        R.string.about_bundled_logo,
        R.string.about_bundled_category_icons,
    )

private fun Context.versionName(): String =
    packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

/** The licence check's report, bundled by the app's build ([LICENCES_ASSET]). */
private fun Context.shippedLicences(): List<Licence> =
    try {
        assets.open(LICENCES_ASSET).use { input ->
            parseLicences(input.bufferedReader().readText())
        }
    } catch (_: IOException) {
        emptyList()
    }

/** The libraries in licensee's report, by name. Unreadable, none. */
fun parseLicences(json: String): List<Licence> =
    try {
        Json { ignoreUnknownKeys = true }
            .decodeFromString<List<Artifact>>(json)
            .map { artifact ->
                Licence(
                    name = artifact.name ?: "${artifact.groupId}:${artifact.artifactId}",
                    version = artifact.version,
                    licences =
                        artifact.spdxLicenses.map { it.identifier } +
                            artifact.unknownLicenses.mapNotNull { it.name },
                )
            }.sortedBy { it.name.lowercase() }
    } catch (_: SerializationException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    }

@Serializable
private class Artifact(
    val groupId: String,
    val artifactId: String,
    val version: String = "",
    val name: String? = null,
    val spdxLicenses: List<Spdx> = emptyList(),
    val unknownLicenses: List<Unknown> = emptyList(),
)

@Serializable
private class Spdx(
    val identifier: String,
)

@Serializable
private class Unknown(
    val name: String? = null,
)

/** The app's source code (ST-23, LI-01). */
const val SOURCE_URL = "https://github.com/callisto-rpg/idejo-android-app"

/** Where the app's build puts licensee's report (app/build.gradle.kts). */
const val LICENCES_ASSET = "licences.json"
