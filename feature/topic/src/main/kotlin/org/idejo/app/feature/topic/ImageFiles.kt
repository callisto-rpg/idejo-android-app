// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** An image of a message, as the viewer shows it (TV-25): its address and alt text. */
data class ViewedImage(
    val url: String,
    val alt: String? = null,
) {
    /** The file's name, from the address's last part. */
    val name: String
        get() =
            url
                .substringBefore('?')
                .substringAfterLast('/')
                .ifBlank { "image" }

    /** Its type, from the name's extension. */
    val mime: String
        get() =
            MimeTypeMap
                .getSingleton()
                .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
                ?.takeIf { it.startsWith("image/") }
                ?: "image/*"
}

/**
 * The image's bytes from the app's image cache, loading it first when needed: through the
 * app's image client, which never carries a token (SI-04). Null when it cannot be had.
 */
internal suspend fun Context.imageBytes(image: ViewedImage): ByteArray? {
    val loader = SingletonImageLoader.get(this)

    fun cached(): ByteArray? =
        loader.diskCache?.let { cache ->
            cache.openSnapshot(image.url)?.use { snapshot ->
                cache.fileSystem.read(snapshot.data) { readByteArray() }
            }
        }
    return withContext(Dispatchers.IO) {
        try {
            cached() ?: run {
                loader.execute(ImageRequest.Builder(this@imageBytes).data(image.url).build())
                cached()
            }
        } catch (_: IOException) {
            null
        }
    }
}

/** **Share**: the image as a file of the cache's shared/ folder, to the app chosen. */
internal suspend fun Context.shareImage(
    image: ViewedImage,
    title: String,
): Boolean {
    val bytes = imageBytes(image) ?: return false
    val file =
        withContext(Dispatchers.IO) {
            val folder = File(cacheDir, "shared").apply { mkdirs() }
            folder.listFiles()?.forEach { it.delete() }
            File(folder, image.name).apply { writeBytes(bytes) }
        }
    val uri = FileProvider.getUriForFile(this, "$packageName.images", file)
    val send =
        Intent(Intent.ACTION_SEND)
            .setType(image.mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    return true
}

/** **Save**: the image written where the member chose (the system's file picker). */
internal suspend fun Context.saveImage(
    image: ViewedImage,
    to: Uri,
): Boolean {
    val bytes = imageBytes(image) ?: return false
    return withContext(Dispatchers.IO) {
        try {
            contentResolver.openOutputStream(to)?.use { it.write(bytes) } != null
        } catch (_: IOException) {
            false
        }
    }
}
