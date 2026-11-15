// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import org.idejo.app.core.data.TutorialBubbles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import org.idejo.app.core.model.ComposerState as ServerComposer

class SlowModeTest {
    private val now = Instant.parse("2026-11-15T10:00:00Z")

    private fun server(
        open: String? = null,
        mayStart: Instant? = null,
        held: Instant? = null,
        typing: Instant? = null,
        window: Int = 15,
    ) = ServerComposer(
        openMessageId = open,
        mayStartNewAt = mayStart,
        heldUntil = held,
        typingSince = typing,
        windowSeconds = window,
        unansweredSeconds = 600,
        now = now,
    )

    @Test
    fun `CO-07 the line runs to may_start_new_at on the device's clock`() {
        // The device's clock is 2 s behind the server's.
        val device = now.toEpochMilli() - 2_000
        val line =
            timerLine(
                server(open = "m_1", mayStart = now.plusSeconds(10)),
                openedAt = now.minusSeconds(5),
                receivedAtMillis = device,
            )!!
        assertEquals(device - 5_000, line.startMillis)
        assertEquals(device + 10_000, line.endMillis)
        assertEquals(1f / 3, line.progress(device), 0.001f)
        assertFalse(line.done(device))
        assertTrue(line.done(device + 10_000))
        assertFalse(line.held)
    }

    @Test
    fun `CO-07 a message closed early holds the next until held_until`() {
        val line = timerLine(server(held = now.plusSeconds(8)), null, now.toEpochMilli())!!
        assertTrue(line.held)
        assertEquals(now.toEpochMilli() + 8_000, line.endMillis)
    }

    @Test
    fun `CO-07 before the first send, the window from the first keystroke`() {
        val line = timerLine(server(typing = now.minusSeconds(3)), null, now.toEpochMilli())!!
        assertEquals(now.toEpochMilli() - 3_000, line.startMillis)
        assertEquals(now.toEpochMilli() + 12_000, line.endMillis)
    }

    @Test
    fun `CO-07 no line without slow mode or without a time`() {
        assertNull(timerLine(server(), null, 0))
        val private = server(open = "m_1", mayStart = now, window = 0).copy(unansweredSeconds = 0)
        assertNull(timerLine(private, null, 0))
        assertNull(timerLine(null, null, 0))
    }

    @Test
    fun `CO-06 the tips follow the message, as the web's`() {
        val typed = ComposerState(text = "Hi")
        val sent = ComposerState(text = "Hi. ", sent = "Hi.", openMessageId = "m_1")
        assertEquals(TutorialTip.EnterToSend, tutorialTip(typed, false, TutorialBubbles.All))
        assertEquals(TutorialTip.Sent, tutorialTip(sent, true, TutorialBubbles.All))
        assertEquals(TutorialTip.Continue, tutorialTip(sent, false, TutorialBubbles.All))
        assertNull(tutorialTip(ComposerState(), false, TutorialBubbles.All))
    }

    @Test
    fun `CO-06 Simple keeps Enter to send and Sent, Off none`() {
        val sent = ComposerState(text = "Hi. ", sent = "Hi.", openMessageId = "m_1")
        assertEquals(TutorialTip.Sent, tutorialTip(sent, true, TutorialBubbles.Simple))
        assertNull(tutorialTip(sent, false, TutorialBubbles.Simple))
        assertNull(tutorialTip(ComposerState(text = "Hi"), false, TutorialBubbles.Off))
    }
}
