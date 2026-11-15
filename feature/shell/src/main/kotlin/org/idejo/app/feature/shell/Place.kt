// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import kotlinx.serialization.Serializable

/**
 * Where the member is in a forum: what the breadcrumbs show (SH-02) and what the sidebar
 * highlights (SH-34). The app's navigation uses these as its typed routes.
 */
@Serializable
sealed interface Place {
    @Serializable
    data object Home : Place

    @Serializable
    data class Category(
        val id: String,
    ) : Place

    /** A topic; with [messageId], open on that message (a search hit, a notification). */
    @Serializable
    data class Topic(
        val id: String,
        val messageId: String? = null,
        /** A message forwarded here from the forward card (CO-17). */
        val forward: String? = null,
        /** Pictures shared to the app go in its composer (CO-14). */
        val share: Boolean = false,
        /** A private conversation (PC-01): its channel is `conversation:<id>`. */
        val private: Boolean = false,
    ) : Place

    /** **Private Conversations** (PC-01): the member's, as cards. */
    @Serializable
    data object Conversations : Place

    @Serializable
    data object Unread : Place

    /**
     * **Filter by tag** (BR-04), with [tags] ticked (slugs, comma separated, as idejo's
     * `GET /topics?tags=`): a tag tapped anywhere.
     */
    @Serializable
    data class Tags(
        val tags: String = "",
    ) : Place

    @Serializable
    data object Search : Place

    @Serializable
    data object Rules : Place

    @Serializable
    data object Help : Place

    @Serializable
    data object AppSettings : Place

    /**
     * **Profile and account** (ST), for the forum open: the overview, or with [section] the
     * key of one of its sections (`SETTINGS_SECTIONS` in feature/settings).
     */
    @Serializable
    data class ProfileAndAccount(
        val section: String? = null,
    ) : Place

    /**
     * **New topic** (CO-19), from a topic's `!new-topic`: in [categoryId], continuing from
     * message [from] with its [body] as the first message, or a topic of its own.
     */
    @Serializable
    data class NewTopic(
        val categoryId: String? = null,
        val from: String? = null,
        val title: String? = null,
        val body: String = "",
    ) : Place
}

/** The forward card over the page (CO-17): a dialog, so the breadcrumbs stay. */
@Serializable
data class ForwardDialog(
    val messageId: String,
    val topicId: String,
)

/** The card for pictures shared to the app (CO-14): which topic they go to. */
@Serializable
data object ShareDialog

/** A member's card over the page (BR-09): a dialog, not a place, so the breadcrumbs stay. */
@Serializable
data class UserCardDialog(
    val id: String,
)

/**
 * An inline link's card over the page (TV-22): the mention as the API gives it. A dialog,
 * as the user card, so the breadcrumbs stay.
 */
@Serializable
data class InlineCardDialog(
    val type: String,
    val text: String,
    val id: String? = null,
    val slug: String? = null,
    val name: String? = null,
)
