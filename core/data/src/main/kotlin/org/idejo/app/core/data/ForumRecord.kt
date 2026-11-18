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
    /** The sidebar's links rolled up into icons (SH-35). */
    val linksRolled: Boolean = false,
    /** This device's push registration with the forum (NT-02); null without one. */
    val push: PushRecord? = null,
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

/** Everything the app keeps: the forums and which one is open (single-forum mode: one). */
@Serializable
data class AppState(
    val forums: List<ForumRecord> = emptyList(),
    val active: String? = null,
    /** The app's one **Tutorial bubbles** choice (SH-21). */
    val tutorialBubbles: TutorialBubbles = TutorialBubbles.All,
    /** When **Dismiss** was last said to the notifications bar (NT-07), epoch milliseconds. */
    val pushBarDismissedAt: Long? = null,
) {
    fun forum(origin: String?): ForumRecord? = forums.firstOrNull { it.origin == origin }

    fun with(record: ForumRecord): AppState =
        copy(forums = forums.filterNot { it.origin == record.origin } + record)
}
