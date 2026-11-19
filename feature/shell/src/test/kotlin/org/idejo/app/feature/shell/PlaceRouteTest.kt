// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The last place opened, as kept for the next launch (MD-05). */
class PlaceRouteTest {
    @Test
    fun `MD-05 a page kept reads back the same, home as none`() {
        listOf(
            Place.Category("c_1"),
            Place.Topic("t_1", "m_9"),
            Place.Topic("p_1", "m_2", private = true),
            Place.Unread,
            Place.Tags("tomatoes"),
            Place.ProfileAndAccount("writing"),
        ).forEach { assertEquals(it, placeOf(requireNotNull(routeOf(it)))) }
        assertNull(routeOf(Place.Home))
    }

    @Test
    fun `MD-05 a topic is kept without the forward or shared pictures it opened with`() {
        val kept =
            placeOf(requireNotNull(routeOf(Place.Topic("t_1", forward = "m_3", share = true))))
        assertEquals(Place.Topic("t_1"), kept)
    }

    @Test
    fun `MD-05 a route this version cannot read opens home`() {
        assertNull(placeOf("""{"type":"a.page.from.later","id":"x"}"""))
        assertNull(placeOf("not json"))
    }
}
