// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.profile

import org.junit.Assert.assertEquals
import org.junit.Test

/** The square kept from a picture (ST-01). */
class CropFrameTest {
    @Test
    fun `ST-01 at first the square is the centre of the picture, its shorter side`() {
        val frame = CropFrame(4000, 3000)
        assertEquals(3000, frame.sideInt)
        assertEquals(500, frame.left)
        assertEquals(0, frame.top)
    }

    @Test
    fun `ST-01 zooming in keeps the centre and shrinks the square`() {
        val frame = CropFrame(4000, 3000).zoomedBy(2f)
        assertEquals(1500, frame.sideInt)
        assertEquals(1250, frame.left)
        assertEquals(750, frame.top)
    }

    @Test
    fun `ST-01 the square never leaves the picture, nor zooms past four times`() {
        val moved = CropFrame(4000, 3000).movedBy(10_000f, -10_000f)
        assertEquals(1000, moved.left)
        assertEquals(0, moved.top)
        assertEquals(750, CropFrame(4000, 3000).zoomedBy(100f).sideInt)
        assertEquals(3000, CropFrame(4000, 3000).zoomedBy(0.1f).sideInt)
    }

    @Test
    fun `AX-04 the move buttons step a tenth of the square`() {
        val frame = CropFrame(4000, 3000).zoomedBy(2f).stepped(1, 1)
        assertEquals(1400, frame.left)
        assertEquals(900, frame.top)
    }
}
