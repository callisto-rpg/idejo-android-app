// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import java.io.IOException

/** What the forum takes (CO-14): `upload_max_bytes` and `upload_types` (extensions). */
data class UploadLimits(
    val maxBytes: Long? = null,
    val types: List<String> = emptyList(),
)

/** A picture read from the picker, the camera, the clipboard, a drop or the share sheet. */
class ImageFile(
    val bytes: ByteArray,
    val type: String,
) {
    val name: String get() = "image." + EXTENSIONS[type].orEmpty().ifEmpty { "jpg" }
}

/** The picture at [uri], read whole; null when it cannot be read or is not an image. */
internal fun readImage(
    resolver: ContentResolver,
    uri: Uri,
): ImageFile? =
    try {
        val type = resolver.getType(uri)?.lowercase() ?: "image/jpeg"
        if (!type.startsWith("image/")) {
            null
        } else {
            resolver.openInputStream(uri)?.use { ImageFile(it.readBytes(), type) }
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

/**
 * [image] as the forum takes it (CO-14): JPEG, PNG, GIF or WebP within `upload_max_bytes` go
 * as they are; any other picture (HEIC from a camera…) or one too large becomes a JPEG, made
 * smaller until it fits. A GIF too large stays refused, as making it smaller would stop it
 * moving. Null when it cannot be made to fit.
 */
internal fun fitted(
    image: ImageFile,
    limits: UploadLimits,
): ImageFile? {
    val max = limits.maxBytes ?: Long.MAX_VALUE
    val taken = taken(image.type, limits)
    return when {
        taken && image.bytes.size <= max -> image
        image.type == GIF -> null
        !taken(JPEG, limits) -> null
        else -> shrunk(image.bytes, max)
    }
}

private fun taken(
    type: String,
    limits: UploadLimits,
): Boolean {
    val extension = EXTENSIONS[type] ?: return false
    return limits.types.isEmpty() ||
        extension in limits.types ||
        (extension == "jpg" && "jpeg" in limits.types)
}

/** A JPEG of [bytes] within [max]: smaller sides, then less quality, until it fits. */
private fun shrunk(
    bytes: ByteArray,
    max: Long,
): ImageFile? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > LONGEST_SIDE) sample *= 2
    var bitmap =
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
    repeat(ATTEMPTS) {
        for (quality in QUALITIES) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= max) return ImageFile(out.toByteArray(), JPEG)
        }
        bitmap = bitmap.scale(bitmap.width / 2, bitmap.height / 2)
    }
    return null
}

private const val JPEG = "image/jpeg"
private const val GIF = "image/gif"
private val EXTENSIONS =
    mapOf(JPEG to "jpg", "image/png" to "png", GIF to "gif", "image/webp" to "webp")
private const val LONGEST_SIDE = 2560
private const val ATTEMPTS = 4
private val QUALITIES = listOf(85, 70, 55)
