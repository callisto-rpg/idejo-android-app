// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * An image full screen (TV-25): pinch or the buttons to zoom, drag to look around, double
 * tap to zoom in or back; **Share** and **Save**. Its alt text is what TalkBack reads.
 */
@Composable
internal fun ImageViewer(
    image: ViewedImage,
    onClose: () -> Unit,
) = Dialog(
    onDismissRequest = onClose,
    properties = DialogProperties(usePlatformDefaultWidth = false),
) { ImageViewerContent(image, onClose) }

/** What the viewer's dialog shows. */
@Composable
internal fun ImageViewerContent(
    image: ViewedImage,
    onClose: () -> Unit,
) {
    val zoom = remember(image) { ZoomState() }
    var note by remember(image) { mutableStateOf<String?>(null) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            AsyncImage(
                image.url,
                image.alt?.takeIf { it.isNotBlank() },
                Modifier.fillMaxSize().zoomable(zoom),
                contentScale = ContentScale.Fit,
            )
            ViewerBar(
                image,
                zoom,
                onClose,
                onNote = { note = it },
                Modifier.align(Alignment.TopCenter),
            )
            note?.let {
                Text(
                    it,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .background(
                            MaterialTheme.colorScheme.inverseSurface,
                            MaterialTheme.shapes.small,
                        ).padding(horizontal = 16.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }
    }
}

/** Close, zoom out and in (AX-04: every gesture has a button), Share, Save. */
@Composable
private fun ViewerBar(
    image: ViewedImage,
    zoom: ZoomState,
    onClose: () -> Unit,
    onNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.image_failed)
    val saved = stringResource(R.string.image_saved)
    val shareTitle = stringResource(R.string.image_share)
    val save =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(image.mime),
        ) { to ->
            to?.let { scope.launch { onNote(if (context.saveImage(image, it)) saved else failed) } }
        }
    Row(modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        BarButton(
            org.idejo.app.core.designsystem.R.drawable.ic_close,
            R.string.image_close,
            onClose,
        )
        Box(Modifier.weight(1f))
        BarButton(R.drawable.ic_zoom_out, R.string.image_zoom_out) { zoom.by(1 / STEP) }
        BarButton(R.drawable.ic_zoom_in, R.string.image_zoom_in) { zoom.by(STEP) }
        BarButton(R.drawable.ic_share, R.string.image_share) {
            scope.launch { if (!context.shareImage(image, shareTitle)) onNote(failed) }
        }
        BarButton(R.drawable.ic_download, R.string.image_save) { save.launch(image.name) }
    }
}

@Composable
private fun BarButton(
    icon: Int,
    label: Int,
    onClick: () -> Unit,
) = IconButton(onClick = onClick) {
    Icon(painterResource(icon), stringResource(label))
}

/** How far in, and where: 1× to 5×, never dragged past the image's edges. */
internal class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var size = IntSize.Zero

    fun by(
        factor: Float,
        pan: Offset = Offset.Zero,
    ) {
        // Back at 1× (within rounding), the image sits where it started.
        scale = (scale * factor).coerceIn(1f, MAX).let { if (it < SNAP) 1f else it }
        if (scale == 1f) {
            offset = Offset.Zero
            return
        }
        val maxX = (scale - 1) * size.width / 2
        val maxY = (scale - 1) * size.height / 2
        offset =
            Offset(
                (offset.x + pan.x).coerceIn(-maxX, maxX),
                (offset.y + pan.y).coerceIn(-maxY, maxY),
            )
    }

    fun toggle() = if (scale > 1f) by(1 / scale) else by(DOUBLE_TAP)
}

private fun Modifier.zoomable(zoom: ZoomState) =
    onSizeChanged { zoom.size = it }
        .pointerInput(zoom) {
            detectTransformGestures {
                _,
                pan,
                factor,
                _,
                ->
                zoom.by(factor, pan)
            }
        }.pointerInput(zoom) { detectTapGestures(onDoubleTap = { zoom.toggle() }) }
        .graphicsLayer {
            scaleX = zoom.scale
            scaleY = zoom.scale
            translationX = zoom.offset.x
            translationY = zoom.offset.y
        }

private const val MAX = 5f
private const val SNAP = 1.01f
private const val STEP = 1.5f
private const val DOUBLE_TAP = 2.5f
