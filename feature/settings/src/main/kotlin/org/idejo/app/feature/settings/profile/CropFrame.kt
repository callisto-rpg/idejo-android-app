// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.profile

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The square of a [width] × [height] picture kept as a profile picture (ST-01): its side is
 * the picture's shorter side divided by [zoom] (1 to [MAX_ZOOM]), around ([centerX],
 * [centerY]) in the picture's pixels, always inside the picture. Plain math, so it is
 * tested without Android.
 */
data class CropFrame(
    val width: Int,
    val height: Int,
    val zoom: Float = 1f,
    val centerX: Float = width / 2f,
    val centerY: Float = height / 2f,
) {
    /** The square's side, in the picture's pixels. */
    val side: Float get() = min(width, height) / zoom.coerceIn(1f, MAX_ZOOM)

    val left: Int get() = (centerX - side / 2).roundToInt().coerceIn(0, width - sideInt)
    val top: Int get() = (centerY - side / 2).roundToInt().coerceIn(0, height - sideInt)
    val sideInt: Int get() = side.roundToInt().coerceIn(1, min(width, height))

    /** [zoom] kept between 1 and [MAX_ZOOM], and the centre moved so the square fits. */
    fun clamped(): CropFrame {
        val z = zoom.coerceIn(1f, MAX_ZOOM)
        val half = min(width, height) / z / 2
        return copy(
            zoom = z,
            centerX = centerX.coerceIn(half, width - half),
            centerY = centerY.coerceIn(half, height - half),
        )
    }

    /** Zoomed by [factor] around the same centre. */
    fun zoomedBy(factor: Float): CropFrame = copy(zoom = zoom * factor).clamped()

    /** Moved by ([dx], [dy]) picture pixels. */
    fun movedBy(
        dx: Float,
        dy: Float,
    ): CropFrame = copy(centerX = centerX + dx, centerY = centerY + dy).clamped()

    /** Moved by a [fraction] of the square's side, for the move buttons (AX-04). */
    fun stepped(
        x: Int,
        y: Int,
        fraction: Float = STEP,
    ): CropFrame = movedBy(x * side * fraction, y * side * fraction)

    companion object {
        const val MAX_ZOOM = 4f
        const val STEP = 0.1f
    }
}
