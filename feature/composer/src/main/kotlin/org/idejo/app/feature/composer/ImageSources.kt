// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Where the composer's pictures come from (CO-14): the photo picker, the camera, the
 * clipboard (the text menu's **Paste image**, Ctrl+V), a drop on the box, the share sheet
 * (at the end, once its topic is picked).
 * Each is read, made to fit what the forum takes, uploaded, then its description asked.
 */
internal class ImageSources(
    /** The picture picked goes at the end (`!image`) or at the caret (the toolbar). */
    val pick: (atEnd: Boolean) -> Unit,
    /** Null on a device without a camera. */
    val takePhoto: (() -> Unit)?,
    /** Pictures from elsewhere, at the caret. */
    val add: (List<Uri>) -> Unit,
    private val context: Context,
) {
    /** The clipboard's pictures, if it holds any. */
    fun clipboardImages(): List<Uri> {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return emptyList()
        if (clipboard.primaryClipDescription?.hasMimeType("image/*") != true) return emptyList()
        return clipboard.primaryClip?.uris().orEmpty()
    }
}

@Composable
internal fun rememberImageSources(
    commanding: Commanding,
    events: CommandEvents,
): ImageSources {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val upload by rememberUpdatedState(events.upload)
    val limits by rememberUpdatedState(events.limits)
    val refused by rememberUpdatedState(events.onImageRefused)
    var atEnd by remember { mutableStateOf(true) }
    val takeShared by rememberUpdatedState(events.takeShared)
    val send = { images: List<suspend () -> ImageFile?>, end: Boolean ->
        scope.launch {
            for (load in images) {
                val image =
                    withContext(Dispatchers.Default) { load()?.let { fitted(it, limits()) } }
                if (image == null) {
                    refused()
                } else {
                    upload(image)?.let { commanding.uploaded(it, end) }
                }
            }
        }
    }
    val fromUris = { uris: List<Uri>, end: Boolean ->
        send(uris.map { uri -> suspend { readImage(context.contentResolver, uri) } }, end)
    }
    LaunchedEffect(Unit) {
        val shared = takeShared()
        if (shared.isNotEmpty()) send(shared.map { image -> suspend { image } }, true)
    }
    val picker =
        rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
            if (uri != null) fromUris(listOf(uri), atEnd)
        }
    val takePhoto = rememberCamera { fromUris(listOf(it), false) }
    return remember(commanding) {
        ImageSources(
            pick = { end ->
                atEnd = end
                picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
            },
            takePhoto = takePhoto,
            add = { fromUris(it, false) },
            context = context,
        )
    }
}

/** The camera's photo, then [onPhoto] with it; null on a device without a camera. */
@Composable
private fun rememberCamera(onPhoto: (Uri) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val taken by rememberUpdatedState(onPhoto)
    var photo by remember { mutableStateOf<Uri?>(null) }
    val camera =
        rememberLauncherForActivityResult(TakePicture()) { done ->
            val uri = photo
            if (done && uri != null) taken(uri)
        }
    val hasCamera =
        remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    if (!hasCamera) return null
    return {
        val uri = cameraFile(context)
        photo = uri
        try {
            camera.launch(uri)
        } catch (_: ActivityNotFoundException) {
            photo = null
        }
    }
}

/** A new file for the camera's photo, in the cache's camera/ folder the provider shares. */
private fun cameraFile(context: Context): Uri {
    val folder = File(context.cacheDir, "camera").apply { mkdirs() }
    folder.listFiles()?.forEach { it.delete() }
    val file = File(folder, "photo-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.images", file)
}

private fun ClipData.uris(): List<Uri> = (0 until itemCount).mapNotNull { getItemAt(it).uri }

/**
 * The box's ways in for pictures from elsewhere: **Paste image** in the text menu while the
 * clipboard holds one, Ctrl+V with a keyboard, and a picture dropped on it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.imagesIn(sources: ImageSources?): Modifier {
    if (sources == null) return this
    val label = stringResource(R.string.composer_paste_image)
    return this
        .appendTextContextMenuComponents {
            val images = sources.clipboardImages()
            if (images.isNotEmpty()) {
                item(PasteImage, label) {
                    sources.add(images)
                    close()
                }
            }
        }.contentReceiver(
            object : ReceiveContentListener {
                override fun onReceive(
                    transferableContent: TransferableContent,
                ): TransferableContent? {
                    if (!transferableContent.hasMediaType(
                            MediaType.Image,
                        )
                    ) {
                        return transferableContent
                    }
                    val uris = transferableContent.clipEntry.clipData.uris()
                    if (uris.isEmpty()) return transferableContent
                    sources.add(uris)
                    return null
                }
            },
        )
}

/** Ctrl+V with a picture on the clipboard pastes the picture. */
internal fun ImageSources.pasted(key: KeyEvent): Boolean {
    if (key.type != KeyEventType.KeyDown || !key.isCtrlPressed || key.key != Key.V) return false
    val images = clipboardImages()
    if (images.isEmpty()) return false
    add(images)
    return true
}

private data object PasteImage
