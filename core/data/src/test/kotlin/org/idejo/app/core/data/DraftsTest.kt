// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The text typed and not sent, kept on the device (OF-03, NF-07). */
class DraftsTest {
    @get:Rule val folder = TemporaryFolder()

    private val garden = "https://garden.example.org/"
    private val bees = "https://bees.example.org/"

    @Test
    fun `OF-03 NF-07 a draft is on disk at once and outlives the app`() {
        val drafts = Drafts(folder.root)
        drafts.put(garden, Drafts.topic("t_1"), "Half a thought")
        drafts.settle()

        // A new process: nothing in memory, the file read again.
        val again = Drafts(folder.root)
        assertEquals("Half a thought", again.get(garden, Drafts.topic("t_1")))
        assertNull(again.get(garden, Drafts.topic("t_2")))
        assertNull(again.get(bees, Drafts.topic("t_1")))
    }

    @Test
    fun `OF-03 a blank draft is removed, and no address names a file`() {
        val drafts = Drafts(folder.root)
        drafts.put(garden, Drafts.newTopic("c_veg"), "Seeds")
        drafts.settle()
        assertTrue(folder.root.walk().none { "garden" in it.name || "c_veg" in it.name })

        drafts.clear(garden, Drafts.newTopic("c_veg"))
        drafts.settle()
        assertNull(Drafts(folder.root).get(garden, Drafts.newTopic("c_veg")))
        assertTrue(folder.root.walk().none { it.isFile })
    }

    @Test
    fun `OF-03 a forum let go of loses its drafts, the others stay`() {
        val drafts = Drafts(folder.root)
        drafts.put(garden, Drafts.topic("t_1"), "Garden")
        drafts.put(bees, Drafts.topic("t_1"), "Bees")
        drafts.forget(garden)
        drafts.settle()

        assertNull(drafts.get(garden, Drafts.topic("t_1")))
        val again = Drafts(folder.root)
        assertNull(again.get(garden, Drafts.topic("t_1")))
        assertEquals("Bees", again.get(bees, Drafts.topic("t_1")))
    }
}
