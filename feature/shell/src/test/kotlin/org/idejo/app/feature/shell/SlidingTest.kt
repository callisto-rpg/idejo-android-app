// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Test

/** The glass's blur follows a panel's slide (SH-10, SH-20, SH-30; SidebarGlass trial). */
class SlidingTest {
    private fun sliding() = Sliding(CoroutineScope(Dispatchers.Unconfined), { true }, {}, {})

    /** Whether reading [fraction] before [width] is measured would hear [change]. */
    private fun hears(change: (Sliding) -> Unit): Boolean {
        val sliding = sliding()
        val reads = mutableSetOf<Any>()
        Snapshot.observe(readObserver = { reads += it }) { sliding.fraction }
        var heard = false
        val handle =
            Snapshot.registerApplyObserver { changed, _ ->
                heard = changed.any { it in reads }
            }
        Snapshot.withMutableSnapshot { change(sliding) }
        Snapshot.sendApplyNotifications()
        handle.dispose()
        return heard
    }

    @Test
    fun `SH-10 the page blurs on a menu's first pull, before the menu is measured`() {
        // The forum and user menus learn their height only once drawn: the blur must hear it.
        assertTrue(hears { it.width = 600f })
    }

    @Test
    fun `SH-10 the page's blur follows the slide even before the menu is measured`() {
        assertTrue(hears { it.drag(40f) })
    }
}
