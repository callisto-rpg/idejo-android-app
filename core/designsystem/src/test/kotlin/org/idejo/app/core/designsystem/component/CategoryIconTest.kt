// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.component

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** idejo's category icons, as the app reads them (TH-05). */
@RunWith(RobolectricTestRunner::class)
class CategoryIconTest {
    private val assets = ApplicationProvider.getApplicationContext<Context>().assets

    private fun shapes(nodes: List<IconNode>): List<IconNode.Shape> =
        nodes.flatMap {
            when (it) {
                is IconNode.Group -> shapes(it.children)
                is IconNode.Shape -> listOf(it)
            }
        }

    @Test
    fun `TH-05 every bundled icon reads, a tile first, then shapes in the two marker colours`() {
        val names = assets.list("category-icons").orEmpty().map { it.removeSuffix(".svg") }
        assertEquals(63, names.size)
        for (name in names) {
            val svg = assets.open("category-icons/$name.svg").use { it.reader().readText() }
            val all = shapes(readIcon(svg))
            assertEquals(name, Paint.Tile, all.first().style.fill)
            val paints = all.flatMap { listOfNotNull(it.style.fill, it.style.stroke) }.toSet()
            // SVG's default fill (black) only where a shape gives none, as idejo checks them.
            val allowed = setOf(Paint.Tile, Paint.Ink, Style().fill)
            assertTrue("$name: $paints", paints.all { it in allowed })
            assertNotNull(name, CategoryIcon.load(assets, name)?.vector(Color.Red, Color.White))
        }
    }

    @Test
    fun `TH-05 a name the app has no drawing for, or not a plain name, gives the initial`() {
        assertNull(CategoryIcon.load(assets, "unicorn"))
        assertNull(CategoryIcon.load(assets, "../../AndroidManifest"))
        assertNull(CategoryIcon.load(assets, "PLANTS"))
    }

    @Test
    fun `TH-05 groups pass their colour on, and rotate around their centre`() {
        val svg =
            """<svg viewBox="0 0 32 32"><rect width="32" height="32" rx="7" fill="#6567e7"/>""" +
                """<g fill="#ffffff" transform="rotate(35 16 16)"><circle cx="16" cy="16" r="3"/>""" +
                """<path d="M0 0 L4 4" fill="none" stroke="#6567E7" stroke-width="2"/></g></svg>"""
        val group = readIcon(svg)[1] as IconNode.Group
        assertEquals(listOf(35f, 16f, 16f), listOf(group.rotate, group.pivotX, group.pivotY))
        val (circle, line) = group.children.map { it as IconNode.Shape }
        assertEquals(Paint.Ink, circle.style.fill)
        assertNull(line.style.fill)
        assertEquals(Paint.Tile, line.style.stroke)
        assertEquals(2f, line.style.strokeWidth)
    }
}
