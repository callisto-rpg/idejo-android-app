// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import org.idejo.app.core.model.Category
import org.idejo.app.core.model.CategoryGroup
import org.idejo.app.core.model.Tag
import org.junit.Assert.assertEquals
import org.junit.Test

/** Filter by tag's groups (BR-04, IX-10). */
class TagGroupTest {
    private val garden = CategoryGroup("g", "Garden", 1)
    private val veg = Category("c_veg", "Vegetables", "vegetables", group = garden, position = 2)
    private val fruit = Category("c_fruit", "Fruit", "fruit", group = garden, position = 1)
    private val chat = Category("c_chat", "Chat", "chat", position = 0)
    private val question = Tag("t_q", "Question", "question", categoryIds = emptyList())
    private val seeds = Tag("t_s", "Seeds", "seeds", categoryIds = listOf("c_veg", "c_fruit"))
    private val pruning = Tag("t_p", "Pruning", "pruning", categoryIds = listOf("c_fruit"))

    private fun List<TagGroup>.shape() =
        map { (it.category?.id ?: if (it.titled) "every" else "untitled") to it.tags.map(Tag::id) }

    @Test
    fun `BR-04 IX-10 every category first, then each category in the home order`() {
        val groups = tagGroups(listOf(pruning, question, seeds), listOf(veg, chat, fruit))
        assertEquals(
            listOf(
                "every" to listOf("t_q"),
                "c_fruit" to listOf("t_p", "t_s"),
                "c_veg" to listOf("t_s"),
            ),
            groups.shape(),
        )
    }

    @Test
    fun `BR-04 IX-10 tags of categories not loaded yet wait untitled at the end`() {
        val groups = tagGroups(listOf(question, seeds), emptyList())
        assertEquals(listOf("every" to listOf("t_q"), "untitled" to listOf("t_s")), groups.shape())
    }

    @Test
    fun `BR-04 an older server, which does not say where tags belong, gets one list`() {
        val old = listOf(Tag("t_q", "Question", "question"), Tag("t_s", "Seeds", "seeds"))
        assertEquals(
            listOf("untitled" to listOf("t_q", "t_s")),
            tagGroups(old, listOf(veg)).shape(),
        )
    }
}
