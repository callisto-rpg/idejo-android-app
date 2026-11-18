// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.profile

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.scale
import org.idejo.app.core.designsystem.theme.IdejoTheme
import org.idejo.app.feature.settings.R
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Crop to square (ST-01): the picture with the square kept, dragged and pinched, or moved
 * and zoomed with buttons (AX-04). **Use this picture** hands the square to [onCrop].
 */
@Composable
internal fun CropDialog(
    bitmap: Bitmap,
    onCrop: (CropFrame) -> Unit,
    onDismiss: () -> Unit,
) {
    var frame by remember(bitmap) { mutableStateOf(CropFrame(bitmap.width, bitmap.height)) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val outline = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.profile_crop_area)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.profile_crop_title),
                    style = IdejoTheme.typography.titleMedium,
                )
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .semantics { contentDescription = description }
                        .pointerInput(bitmap) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                // Screen pixels to the picture's: the square fills the canvas.
                                val perPixel = frame.side / size.width
                                frame =
                                    frame
                                        .movedBy(-pan.x * perPixel, -pan.y * perPixel)
                                        .zoomedBy(zoom)
                            }
                        },
                ) {
                    val side = size.width.toInt()
                    drawImage(
                        image,
                        srcOffset = IntOffset(frame.left, frame.top),
                        srcSize = IntSize(frame.sideInt, frame.sideInt),
                        dstSize = IntSize(side, side),
                    )
                    // The round picture people will see.
                    drawCircle(
                        outline,
                        radius = size.width / 2 - 2.dp.toPx(),
                        center = Offset(size.width / 2, size.height / 2),
                        style = Stroke(2.dp.toPx()),
                    )
                }
                CropButtons(onFrame = { frame = it(frame) })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = onDismiss,
                    ) { Text(stringResource(R.string.profile_cancel)) }
                    TextButton(onClick = { onCrop(frame) }) {
                        Text(stringResource(R.string.profile_crop_use))
                    }
                }
            }
        }
    }
}

/** Zoom and move without gestures (AX-04). */
@Composable
private fun CropButtons(onFrame: ((CropFrame) -> CropFrame) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onFrame { it.zoomedBy(1 / ZOOM_STEP) } }) {
                Text(stringResource(R.string.profile_crop_zoom_out))
            }
            OutlinedButton(onClick = { onFrame { it.zoomedBy(ZOOM_STEP) } }) {
                Text(stringResource(R.string.profile_crop_zoom_in))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MoveButton(R.string.profile_crop_left) { onFrame { it.stepped(-1, 0) } }
            MoveButton(R.string.profile_crop_up) { onFrame { it.stepped(0, -1) } }
            MoveButton(R.string.profile_crop_down) { onFrame { it.stepped(0, 1) } }
            MoveButton(R.string.profile_crop_right) { onFrame { it.stepped(1, 0) } }
        }
    }
}

@Composable
private fun MoveButton(
    label: Int,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) { Text(stringResource(label)) }
}

private const val ZOOM_STEP = 1.25f

/** The picture at [uri], at most [LONGEST_SIDE] on its longer side, upright; null if none. */
internal fun decodePicture(
    resolver: ContentResolver,
    uri: Uri,
): Bitmap? =
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder turns the picture as the camera's EXIF says.
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(resolver, uri),
            ) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > LONGEST_SIDE) {
                    val ratio = LONGEST_SIDE.toFloat() / longest
                    decoder.setTargetSize(
                        (info.size.width * ratio).toInt().coerceAtLeast(1),
                        (info.size.height * ratio).toInt().coerceAtLeast(1),
                    )
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > LONGEST_SIDE) sample *= 2
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(
                    it,
                    null,
                    BitmapFactory.Options().apply {
                        inSampleSize =
                            sample
                    },
                )
            }
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

/** [frame] of [bitmap] as a JPEG of at most [PICTURE_SIDE] pixels a side. */
internal fun croppedJpeg(
    bitmap: Bitmap,
    frame: CropFrame,
): ByteArray {
    val square = Bitmap.createBitmap(bitmap, frame.left, frame.top, frame.sideInt, frame.sideInt)
    val side = minOf(frame.sideInt, PICTURE_SIDE)
    val scaled = if (side == frame.sideInt) square else square.scale(side, side)
    return ByteArrayOutputStream()
        .also {
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, it)
        }.toByteArray()
}

private const val LONGEST_SIDE = 2048
private const val PICTURE_SIDE = 512
private const val QUALITY = 90
