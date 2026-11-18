// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import org.idejo.app.core.data.Access
import org.idejo.app.core.data.ForumRecord
import org.idejo.app.core.data.SidebarCategory
import org.idejo.app.core.data.SidebarSection
import org.idejo.app.core.data.SidebarState
import org.idejo.app.core.model.Category
import org.idejo.app.core.model.Discovery
import org.idejo.app.core.model.Me
import org.idejo.app.core.model.Topic
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the frame decides from the forum's state. */
class ShellStateTest {
    private val following =
        SidebarState(
            sections =
                listOf(
                    SidebarSection(
                        null,
                        listOf(
                            SidebarCategory(
                                Category("c_1", "Vegetables", "vegetables"),
                                listOf(Topic("t_1", "Tomatoes")),
                            ),
                        ),
                    ),
                ),
        )

    private val offered =
        ShellState(
            record =
                ForumRecord(
                    "https://garden.example.org/",
                    Discovery("Garden club", "https://garden.example.org/"),
                ),
            access = Access.SignedIn,
            sidebar = following,
            me = Me("u_ada", "Ada", welcomed = true),
            pushOn = true,
        )

    @Test
    fun `NT-07 the bar is offered as the web's banner is`() {
        assertTrue(offered.pushBarOffered)
        assertFalse(offered.copy(pushOn = false).pushBarOffered)
        assertFalse(offered.copy(me = offered.me?.copy(welcomed = false)).pushBarOffered)
        assertFalse(offered.copy(sidebar = SidebarState()).pushBarOffered)
        assertFalse(offered.copy(pushBarQuiet = true).pushBarOffered)
        assertFalse(offered.copy(access = Access.Visitor).pushBarOffered)
    }
}
