// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.profile

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.designsystem.component.Avatar
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.core.designsystem.theme.parseHexColor
import org.idejo.app.core.markdown.IdejoMarkdown
import org.idejo.app.core.markdown.MarkdownActions
import org.idejo.app.core.markdown.MarkdownBody
import org.idejo.app.core.model.Me
import org.idejo.app.feature.settings.R
import org.idejo.app.feature.settings.parts.ErrorState
import org.idejo.app.feature.settings.parts.LoadingState
import org.idejo.app.feature.settings.parts.ProblemText
import org.idejo.app.feature.settings.parts.SaveForAllForums
import org.idejo.app.feature.settings.parts.SectionHeader

/** The forms and pictures of **Profile** (ST-01). */
enum class ProfilePart { Name, Bio, Picture, DarkPicture }

/**
 * **Profile** (ST-01): the name and user tag, the bio in Markdown with **Preview**, the
 * picture and the dark-mode picture. [name], [tag] and [bio] are what the member is typing:
 * null until the server's answer fills them.
 */
@Immutable
data class ProfileUiState(
    val loading: Boolean = true,
    val loadProblem: ApiProblem? = null,
    /** The member as the server last answered. */
    val me: Me? = null,
    val name: String? = null,
    val tag: String? = null,
    val bio: String? = null,
    /** The form or picture on its way to the server: its button waits. */
    val saving: ProfilePart? = null,
    /** The part the server just took: "Profile saved." and the like. */
    val saved: ProfilePart? = null,
    /** Why [problemPart] was not saved; what was typed stays. */
    val problem: ApiProblem? = null,
    val problemPart: ProfilePart? = null,
    /** The picture chosen could not be read. */
    val unreadable: Boolean = false,
    /** The forum's address, for pictures given as paths. */
    val origin: String = "",
) {
    internal val server: Me? get() = me
}

/** What the member does on **Profile**. */
sealed interface ProfileEvent {
    data class EditName(
        val text: String,
    ) : ProfileEvent

    data class EditTag(
        val text: String,
    ) : ProfileEvent

    data class EditBio(
        val text: String,
    ) : ProfileEvent

    data object SaveName : ProfileEvent

    data object SaveBio : ProfileEvent

    /** A square picture, cropped on the device, for the picture or the dark one. */
    class SetPicture(
        val dark: Boolean,
        val bytes: ByteArray,
        val type: String,
    ) : ProfileEvent

    data class RemovePicture(
        val dark: Boolean,
    ) : ProfileEvent

    /** The picture picked could not be read. */
    data object Unreadable : ProfileEvent

    data object Retry : ProfileEvent
}

@Composable
fun ProfileRoute(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileScreen(state, viewModel::onEvent, modifier) { SaveForAllForums(it) }
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onEvent: (ProfileEvent) -> Unit,
    modifier: Modifier = Modifier,
    everywhere: @Composable (sections: List<String>) -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            state.loadProblem != null -> {
                SectionHeader(stringResource(R.string.profile_name))
                ErrorState(state.loadProblem, onRetry = { onEvent(ProfileEvent.Retry) })
            }

            state.loading || state.me == null -> {
                SectionHeader(stringResource(R.string.profile_name))
                LoadingState()
            }

            else -> {
                NameForm(state, onEvent)
                BioForm(state, onEvent)
                Pictures(state, state.me, onEvent)
                // One button for the page: the web's three forms are one section here.
                everywhere(listOf("profile", "bio", "avatar"))
            }
        }
    }
}

@Composable
private fun NameForm(
    state: ProfileUiState,
    onEvent: (ProfileEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.profile_name))
        OutlinedTextField(
            value = state.name.orEmpty(),
            onValueChange = { onEvent(ProfileEvent.EditName(it.take(NAME_MAX))) },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.profile_display_name)) },
            singleLine = true,
        )
        OutlinedTextField(
            value = state.tag.orEmpty(),
            onValueChange = { onEvent(ProfileEvent.EditTag(it.take(TAG_MAX))) },
            Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.profile_user_tag)) },
            singleLine = true,
        )
        Button(
            onClick = { onEvent(ProfileEvent.SaveName) },
            enabled = state.saving == null,
        ) { Text(stringResource(R.string.profile_save)) }
        Outcome(state, ProfilePart.Name, R.string.profile_saved)
    }
}

@Composable
private fun BioForm(
    state: ProfileUiState,
    onEvent: (ProfileEvent) -> Unit,
) {
    var preview by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.profile_bio))
        Text(
            stringResource(R.string.profile_bio_help),
            style = IdejoTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = !preview,
                onClick = { preview = false },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text(stringResource(R.string.profile_write)) }
            SegmentedButton(
                selected = preview,
                onClick = { preview = true },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text(stringResource(R.string.profile_preview)) }
        }
        val bio = state.bio.orEmpty()
        if (preview) {
            val document = remember(bio) { IdejoMarkdown.parse(bio) }
            var revealed by remember { mutableStateOf(emptySet<Int>()) }
            MarkdownBody(
                document,
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                actions = MarkdownActions(onReveal = { revealed = revealed + it }),
                revealed = revealed,
            )
        } else {
            OutlinedTextField(
                value = bio,
                onValueChange = { onEvent(ProfileEvent.EditBio(it)) },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.profile_bio_markdown)) },
                minLines = 4,
            )
        }
        Button(
            onClick = { onEvent(ProfileEvent.SaveBio) },
            enabled = state.saving == null,
        ) { Text(stringResource(R.string.profile_save_bio)) }
        Outcome(state, ProfilePart.Bio, R.string.profile_bio_saved)
    }
}

@Composable
private fun Pictures(
    state: ProfileUiState,
    me: Me,
    onEvent: (ProfileEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.profile_avatar))
        PictureRow(state, me, me.avatarUrl, dark = false, onEvent)
        Outcome(state, ProfilePart.Picture, R.string.profile_avatar_saved)
        SectionHeader(stringResource(R.string.profile_dark_picture))
        Text(
            stringResource(R.string.profile_dark_help),
            style = IdejoTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PictureRow(state, me, me.avatarDarkUrl, dark = true, onEvent)
        Outcome(state, ProfilePart.DarkPicture, R.string.profile_dark_saved)
        if (state.unreadable) {
            Text(
                stringResource(R.string.profile_unreadable),
                style = IdejoTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** A picture, **Choose a picture**, and **Remove** when there is one. */
@Composable
private fun PictureRow(
    state: ProfileUiState,
    me: Me,
    url: String?,
    dark: Boolean,
    onEvent: (ProfileEvent) -> Unit,
) {
    val resolver = LocalContext.current.contentResolver
    var picked by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { picked = it }
    val send by rememberUpdatedState(onEvent)
    LaunchedEffect(picked) {
        val uri = picked ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) { decodePicture(resolver, uri) }
        if (bitmap == null) send(ProfileEvent.Unreadable)
        picked = null
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Picture(me, url, state.origin)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                enabled = state.saving == null,
            ) { Text(stringResource(R.string.profile_choose)) }
            if (url != null) {
                OutlinedButton(
                    onClick = { onEvent(ProfileEvent.RemovePicture(dark)) },
                    enabled = state.saving == null,
                ) {
                    Text(
                        stringResource(
                            if (dark) {
                                R.string.profile_remove_dark
                            } else {
                                R.string.profile_remove_avatar
                            },
                        ),
                    )
                }
            }
        }
    }
    bitmap?.let { chosen ->
        CropDialog(
            chosen,
            onCrop = { frame ->
                bitmap = null
                onEvent(ProfileEvent.SetPicture(dark, croppedJpeg(chosen, frame), "image/jpeg"))
            },
            onDismiss = { bitmap = null },
        )
    }
}

/** The member's picture at [url], or their initials. */
@Composable
private fun Picture(
    me: Me,
    url: String?,
    origin: String,
) {
    Avatar(
        me.displayName,
        parseHexColor(me.colour) ?: MaterialTheme.colorScheme.tertiary,
        size = 56.dp,
        image =
            url?.let { address ->
                {
                    AsyncImage(
                        if (address.startsWith("/")) origin.trimEnd('/') + address else address,
                        null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            },
    )
}

/** "Profile saved." once the server took [part], or why it did not. */
@Composable
private fun Outcome(
    state: ProfileUiState,
    part: ProfilePart,
    saved: Int,
) {
    when {
        state.problem != null && state.problemPart == part -> {
            ProblemText(state.problem)
        }

        state.saved == part -> {
            Text(
                stringResource(saved),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = IdejoTheme.typography.bodyMedium,
            )
        }
    }
}

private const val NAME_MAX = 60
private const val TAG_MAX = 32

@Preview
@Composable
private fun ProfileScreenPreview() {
    IdejoTheme {
        ProfileScreen(
            ProfileUiState(
                loading = false,
                me = Me(id = "u_ada", displayName = "Ada Lovelace", userTag = "ada"),
                name = "Ada Lovelace",
                tag = "ada",
                bio = "Analyst of **engines**.",
            ),
            onEvent = {},
        )
    }
}
