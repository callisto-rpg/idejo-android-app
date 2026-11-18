// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.serialization.SerializationException
import org.idejo.app.core.api.IdejoJson
import org.idejo.app.core.data.PendingLinks
import org.idejo.app.core.model.PushPayload

/** The forum a push came from, as the app keeps it. */
data class PushForum(
    val origin: String,
    val name: String,
    /** The forum's logo, already in the image cache, for the group (NT-05). */
    val logo: Bitmap? = null,
)

/**
 * Shows a forum's push (NT-03 to NT-05): its title and body, on its kind's channel in the
 * forum's group of channels; a later push with the same `tag` replaces it; `app_badge` is
 * the launcher badge. A tap opens `navigate` in the app, in that forum (NT-04). While the app
 * is on screen the bubbles say it already (NT-01), so only test pushes show then.
 */
class PushNotifier(
    private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    /** [content], a decrypted push; false when nothing was shown. */
    fun show(
        forum: PushForum,
        content: ByteArray,
        onScreen: Boolean,
    ): Boolean {
        val payload = parse(content) ?: return false
        val notice = payload.notification ?: return false
        val kind = PushKind.of(notice.tag)
        if (onScreen && notice.tag != TEST) return false
        if (!allowed()) return false
        channels(forum)
        val tag = forum.origin + "|" + (notice.tag ?: "untagged")
        val notification =
            NotificationCompat
                .Builder(context, channelId(forum.origin, kind))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(notice.title)
                .setContentText(notice.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notice.body))
                .setSubText(forum.name)
                .setLargeIcon(forum.logo)
                .setGroup(forum.origin)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setContentIntent(open(forum.origin, notice.navigate ?: forum.origin, tag))
                .apply { payload.appBadge?.let { setNumber(it) } }
                .build()
        val summary =
            NotificationCompat
                .Builder(context, channelId(forum.origin, kind))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(forum.name)
                .setSubText(forum.name)
                .setLargeIcon(forum.logo)
                .setGroup(forum.origin)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(open(forum.origin, forum.origin, forum.origin))
                .build()
        notify(tag, notification)
        notify(forum.origin + "|summary", summary)
        return true
    }

    /** The forum's channels (NT-05), in a group named after it. */
    fun channels(forum: PushForum) {
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        system.createNotificationChannelGroup(NotificationChannelGroup(forum.origin, forum.name))
        val channels =
            PushKind.entries.map { kind ->
                NotificationChannel(
                    channelId(forum.origin, kind),
                    context.getString(kind.label),
                    if (kind == PushKind.Followed) {
                        NotificationManager.IMPORTANCE_DEFAULT
                    } else {
                        NotificationManager.IMPORTANCE_HIGH
                    },
                ).apply { group = forum.origin }
            }
        system.createNotificationChannels(channels)
    }

    /** The forum's channels go with it, when it leaves the app. */
    fun forget(origin: String) {
        context
            .getSystemService(
                NotificationManager::class.java,
            )?.deleteNotificationChannelGroup(origin)
    }

    private fun allowed(): Boolean =
        manager.areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) ==
                    PackageManager.PERMISSION_GRANTED
            )

    private fun notify(
        tag: String,
        notification: android.app.Notification,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.notify(tag, ID, notification)
    }

    /** The app, opening [url] in [origin]'s forum (NT-04); only the app opens it. */
    private fun open(
        origin: String,
        url: String,
        tag: String,
    ): PendingIntent {
        val intent =
            (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
                .setAction(Intent.ACTION_VIEW)
                .setData(url.toUri())
                .setPackage(context.packageName)
                .putExtra(PendingLinks.EXTRA_FORUM, origin)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            tag.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun parse(content: ByteArray): PushPayload? =
        try {
            IdejoJson.decodeFromString<PushPayload>(content.decodeToString())
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    companion object {
        private const val ID = 1
        private const val TEST = "test"

        fun channelId(
            origin: String,
            kind: PushKind,
        ): String = "$origin#${kind.id}"

        /** The logo at [path] (the image cache's file), small enough for a notification. */
        fun logoAt(path: String): Bitmap? = BitmapFactory.decodeFile(path)
    }
}
