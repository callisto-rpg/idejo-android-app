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
    ) : Place

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
}

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
