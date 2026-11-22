// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.serialization.Serializable
import org.idejo.app.core.model.Discovery
import org.idejo.app.core.model.ServerTheme

/** How a forum's pages look (TH-03), chosen by the member, per account. */
@Serializable
enum class ThemeMode { System, Light, Dark }

/**
 * **Tutorial bubbles** (SH-21; idejo's member guide, "The keys"): All, Simple (only **Enter to
 * send** and **Sent!**) or Off. idejo's API has no such setting, so it is kept on the device,
 * one choice for every forum (steward decision, 2026-11-15).
 */
@Serializable
enum class TutorialBubbles { All, Simple, Off }

/** Text size inside messages (ST-22): Default follows Android's font size alone. */
@Serializable
enum class MessageText(
    val scale: Float,
) {
    Small(SMALL),
    Default(1f),
    Large(LARGE),
    Larger(LARGER),
}

private const val SMALL = 0.85f
private const val LARGE = 1.15f
private const val LARGER = 1.3f

/** The image cache's limits to choose from (ST-21), in megabytes; 200 by default. */
const val DEFAULT_CACHE_MB = 200
val CACHE_LIMITS_MB = listOf(100, DEFAULT_CACHE_MB, 500, 1000)

/** How the app looks and feels on this device (ST-22), the same in every forum. */
data class AppDisplay(
    val messageText: MessageText = MessageText.Default,
    val haptics: Boolean = true,
    val reduceMotion: Boolean = false,
)

/** Compact or Expanded topic lists (BR-03), per forum. */
@Serializable
enum class TopicView { Compact, Expanded }

/**
 * A forum the app holds, as it is kept on the device. [sealedToken] is the token sealed
 * by the TokenVault; null for a visitor (SI-05) or after signing out.
 */
@Serializable
data class ForumRecord(
    val origin: String,
    val discovery: Discovery,
    val sealedToken: String? = null,
    val tokenId: String? = null,
    val userId: String? = null,
    /** The token was refused (SI-09, SI-12): "You were signed out of {name}". */
    val signedOut: Boolean = false,
    /** The last theme read from `GET /server` (TH-06), so the forum opens in its colours. */
    val theme: ServerTheme? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val topicView: TopicView = TopicView.Compact,
    /** Categories folded to topics with news (SH-33). */
    val folded: Set<String> = emptySet(),
    /** The sidebar's links rolled up into icons (SH-35), as they start. */
    val linksRolled: Boolean = true,
    /** This device's push registration with the forum (NT-02); null without one. */
    val push: PushRecord? = null,
    /** The page last open here (MD-05), as the app's route writes it; null for home. */
    val lastPlace: String? = null,
) {
    val name: String get() = discovery.name
}

/**
 * What the forum keeps of this device's pushes (NT-02): its registration, and the endpoint,
 * key and VAPID key it was made with, so a change is sent again and nothing else is.
 */
@Serializable
data class PushRecord(
    val registrationId: String,
    val endpoint: String,
    val publicKey: String,
    val vapidKey: String? = null,
) {
    /** The endpoint is the distributor's secret address: never in a log. */
    override fun toString(): String = "PushRecord($registrationId)"
}

/** The inbox's **Topics** toolbar (IB-03): the view, **Unread only**, **Filter** by forum. */
@Serializable
data class InboxTopicChoices(
    val view: TopicView = TopicView.Compact,
    val unreadOnly: Boolean = false,
    /** Forums filtered out, by origin: a forum connected later shows. */
    val hidden: Set<String> = emptySet(),
)

/** The app's two modes (MD-02, MD-03). */
@Serializable
enum class AppMode {
    /** One forum, typed by address (MD-03). */
    Single,

    /** Signed in to a group's inbox, with every joined forum one tap away (MD-02). */
    IdejoId,
}

/**
 * Everything the app keeps: the forums and which one is open. Single-forum mode keeps one;
 * idejo ID mode keeps the group's [inbox] beside any number of forums, [active] null while
 * the inbox is on screen.
 */
@Serializable
data class AppState(
    val forums: List<ForumRecord> = emptyList(),
    val active: String? = null,
    val mode: AppMode = AppMode.Single,
    /** idejo ID mode's inbox (`https://my.idejo.org/` by default), signed in as a forum is. */
    val inbox: ForumRecord? = null,
    /**
     * The group's forums the member has been shown (SI-07), by origin: one joined since, not
     * among them, is new (its Connect card, the logo's dot).
     */
    val seenForums: Set<String> = emptySet(),
    /** The inbox's Topics toolbar (IB-03), remembered. */
    val inboxTopics: InboxTopicChoices = InboxTopicChoices(),
    /** The app's one **Tutorial bubbles** choice (SH-21). */
    val tutorialBubbles: TutorialBubbles = TutorialBubbles.All,
    /** When **Dismiss** was last said to the notifications bar (NT-07), epoch milliseconds. */
    val pushBarDismissedAt: Long? = null,
    /** The image cache's limit (ST-21), in megabytes. */
    val cacheLimitMb: Int = DEFAULT_CACHE_MB,
    /** Text size inside messages (ST-22), over Android's own font size. */
    val messageText: MessageText = MessageText.Default,
    /** Haptics (ST-22): Android's touch feedback in the app. */
    val haptics: Boolean = true,
    /** Reduced motion (ST-22) even when Android's animations are on. */
    val reduceMotion: Boolean = false,
    /** The inbox's tab last shown (MD-05), by name. */
    val inboxTab: String? = null,
    /** Push turned off on this device (App settings, NT-06): no forum registers. */
    val pushOff: Boolean = false,
) {
    fun forum(origin: String?): ForumRecord? = forums.firstOrNull { it.origin == origin }

    /** The forum or the inbox at [origin]. */
    fun record(origin: String?): ForumRecord? =
        forum(origin) ?: inbox?.takeIf { it.origin == origin }

    /** [record] kept: in its place when held already (SH-12's order), else last. */
    fun with(record: ForumRecord): AppState =
        if (forums.any { it.origin == record.origin }) {
            copy(forums = forums.map { if (it.origin == record.origin) record else it })
        } else {
            copy(forums = forums + record)
        }
}
