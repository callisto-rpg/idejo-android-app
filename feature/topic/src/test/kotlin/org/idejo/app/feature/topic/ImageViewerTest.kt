// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.topic

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The image viewer's zoom and the file it shares or saves (TV-25). */
@RunWith(RobolectricTestRunner::class)
class ImageViewerTest {
    @Test
    fun `TV-25 zoom stays between 1x and 5x and never past the image's edges`() {
        val zoom = ZoomState().apply { size = IntSize(400, 300) }
        zoom.by(0.5f)
        assertEquals(1f, zoom.scale)
        zoom.by(2f, pan = Offset(1000f, -1000f))
        assertEquals(2f, zoom.scale)
        assertEquals(Offset(200f, -150f), zoom.offset)
        zoom.by(10f)
        assertEquals(5f, zoom.scale)
        zoom.by(1 / 5f)
        assertEquals(Offset.Zero, zoom.offset)
    }

    @Test
    fun `TV-25 a double tap zooms in, and back out`() {
        val zoom = ZoomState().apply { size = IntSize(400, 300) }
        zoom.toggle()
        assertEquals(2.5f, zoom.scale)
        zoom.toggle()
        assertEquals(1f, zoom.scale)
    }

    @Test
    fun `TV-25 the file keeps the image's name and type`() {
        val png = ViewedImage("https://garden.example/uploads/abc123.png?v=2", "Beds")
        assertEquals("abc123.png", png.name)
        assertEquals("image/png", png.mime)
        assertEquals("image/*", ViewedImage("https://garden.example/uploads/abc").mime)
    }
}
