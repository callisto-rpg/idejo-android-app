// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.idejo.app.core.data.PendingLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** A forum's pushes as Android notifications (NT-03 to NT-05, NT-09). */
@RunWith(RobolectricTestRunner::class)
class PushNotifierTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val system = app.getSystemService(NotificationManager::class.java)
    private val notifier = PushNotifier(app)
    private val garden = PushForum("https://garden.example.org/", "Garden club")

    @Before
    fun allow() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun push(
        tag: String,
        title: String = "Mina mentioned you in Tomatoes",
        badge: Int? = 3,
    ) = """
        {"web_push": 8030,
         "notification": {"title": "$title", "body": "Sungold every year",
                          "navigate": "https://garden.example.org/m/m_1?unread",
                          "tag": "$tag", "lang": "en"},
         "app_badge": ${badge ?: "null"}, "later": {"ignored": true}}
        """.trimIndent().encodeToByteArray()

    private val shown: List<Notification>
        get() = shadowOf(system).allNotifications.filterNot { it.isGroupSummary() }

    private fun Notification.isGroupSummary() = flags and Notification.FLAG_GROUP_SUMMARY != 0

    @Test
    fun `NT-03 the push's title and body, its badge, in the forum's group`() {
        assertTrue(notifier.show(garden, push("mentions"), onScreen = false))

        val notification = shown.single()
        val extras = notification.extras
        assertEquals(
            "Mina mentioned you in Tomatoes",
            extras.getString(NotificationCompat.EXTRA_TITLE),
        )
        assertEquals("Sungold every year", extras.getString(NotificationCompat.EXTRA_TEXT))
        assertEquals("Garden club", extras.getString(NotificationCompat.EXTRA_SUB_TEXT))
        assertEquals(3, notification.number)
        assertEquals(garden.origin, notification.group)
        assertTrue(shadowOf(system).allNotifications.any { it.isGroupSummary() })
    }

    @Test
    fun `NT-03 a push with the same tag replaces the earlier one, per forum`() {
        notifier.show(garden, push("mentions", "First"), onScreen = false)
        notifier.show(garden, push("mentions", "Second"), onScreen = false)
        notifier.show(garden, push("reply-t_1", "A reply"), onScreen = false)
        val other = garden.copy(origin = "https://bees.example.org/", name = "Bees")
        notifier.show(other, push("mentions", "Bees"), onScreen = false)

        val titles =
            shown
                .mapNotNull {
                    it.extras.getString(
                        NotificationCompat.EXTRA_TITLE,
                    )
                }.sorted()
        assertEquals(listOf("A reply", "Bees", "Second"), titles)
    }

    @Test
    fun `NT-05 each kind on its own channel, the forum's channels in a group of its name`() {
        notifier.show(garden, push("reply-t_1"), onScreen = false)
        notifier.show(garden, push("role-mentions"), onScreen = false)

        val channels = shown.map { it.channelId }.toSet()
        assertEquals(
            setOf(
                PushNotifier.channelId(garden.origin, PushKind.Replies),
                PushNotifier.channelId(garden.origin, PushKind.RoleMentions),
            ),
            channels,
        )
        val group = system.getNotificationChannelGroup(garden.origin)
        assertEquals("Garden club", group.name)
        val names =
            system.notificationChannels
                .filter {
                    it.group == garden.origin
                }.map { it.name.toString() }
        assertTrue(
            names.containsAll(
                listOf(
                    "Replies",
                    "Mentions",
                    "Role mentions",
                    "Followed topics",
                    "Private messages",
                    "Warnings",
                ),
            ),
        )
    }

    @Test
    fun `NT-04 a tap opens the push's address in the app, in its forum`() {
        notifier.show(garden, push("mentions"), onScreen = false)

        val intent = shadowOf(shown.single().contentIntent).savedIntent
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://garden.example.org/m/m_1?unread", intent.dataString)
        assertEquals(garden.origin, intent.getStringExtra(PendingLinks.EXTRA_FORUM))
        assertEquals(app.packageName, intent.`package`)
    }

    @Test
    fun `NT-09 on screen the bubbles say it, so only a test push shows`() {
        assertFalse(notifier.show(garden, push("mentions"), onScreen = true))
        assertTrue(shown.isEmpty())
        assertTrue(notifier.show(garden, push("test", "Test notification"), onScreen = true))
        assertEquals(
            PushNotifier.channelId(garden.origin, PushKind.Other),
            shown.single().channelId,
        )
    }

    @Test
    fun `NT-03 what is not idejo's payload shows nothing`() {
        assertFalse(notifier.show(garden, "not json".encodeToByteArray(), onScreen = false))
        assertFalse(notifier.show(garden, "{}".encodeToByteArray(), onScreen = false))
        assertTrue(shown.isEmpty())
    }

    @Test
    fun `NT-07 without the permission nothing shows`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(notifier.show(garden, push("mentions"), onScreen = false))
    }
}
