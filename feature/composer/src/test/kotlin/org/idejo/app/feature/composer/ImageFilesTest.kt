// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** Pictures made to fit what the forum takes (CO-14). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageFilesTest {
    private val forum = UploadLimits(200_000, listOf("jpg", "jpeg", "png", "gif"))

    private fun picture(
        side: Int,
        format: Bitmap.CompressFormat,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val noise = java.util.Random(side.toLong())
        val pixels = IntArray(side * side) { noise.nextInt() or OPAQUE }
        bitmap.setPixels(pixels, 0, side, 0, 0, side, side)
        return ByteArrayOutputStream().also { bitmap.compress(format, 100, it) }.toByteArray()
    }

    @Test
    fun `CO-14 a picture the forum takes, within its size, goes as it is`() {
        val image = ImageFile(picture(40, Bitmap.CompressFormat.PNG), "image/png")
        assertSame(image, fitted(image, forum))
        assertEquals("image.png", image.name)
    }

    @Test
    fun `CO-14 one too large or of another type becomes a smaller JPEG`() {
        val large = ImageFile(picture(600, Bitmap.CompressFormat.PNG), "image/png")
        assertTrue(large.bytes.size > forum.maxBytes!!)
        val fit = fitted(large, forum)!!
        assertEquals("image/jpeg", fit.type)
        assertTrue(fit.bytes.size <= forum.maxBytes!!)
        assertTrue(BitmapFactory.decodeByteArray(fit.bytes, 0, fit.bytes.size) != null)

        val webp = ImageFile(picture(40, Bitmap.CompressFormat.PNG), "image/webp")
        assertEquals("image/jpeg", fitted(webp, forum)!!.type)
    }

    @Test
    fun `CO-14 a GIF too large, or what is no picture, is refused`() {
        assertNull(fitted(ImageFile(ByteArray(300_000), "image/gif"), forum))
        assertNull(fitted(ImageFile(ByteArray(10), "image/heic"), forum))
    }

    @Test
    fun `CO-14 shared pictures are read at once and taken once`() {
        val shared = SharedImages()
        val resolver =
            org.robolectric.RuntimeEnvironment
                .getApplication()
                .contentResolver
        val uri = android.net.Uri.parse("content://elsewhere/photo/1")
        val bytes = picture(10, Bitmap.CompressFormat.PNG)
        org.robolectric.Shadows
            .shadowOf(resolver)
            .registerInputStream(uri, bytes.inputStream())
        runTest { shared.offer(resolver, listOf(uri)) }
        val taken = shared.take()
        assertEquals(listOf(bytes.size), taken.map { it.bytes.size })
        assertEquals(emptyList<ImageFile>(), shared.take())
    }
}

private const val OPAQUE = 0xFF shl 24
