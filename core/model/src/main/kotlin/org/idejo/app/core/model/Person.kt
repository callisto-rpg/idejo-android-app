// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A role as shown beside a name, or listed by the server. */
@Serializable
data class Role(
    val name: String,
    val id: String? = null,
    val colour: String? = null,
    val slug: String? = null,
)

/**
 * Someone, as the API shows a person; also a message's author. An opening message of a
 * topical topic has no author: [id] null, [topical] true, `display_name` "the topic".
 */
@Serializable
data class Person(
    val id: String? = null,
    @SerialName("display_name") val displayName: String,
    val uri: String? = null,
    val username: String? = null,
    @SerialName("user_tag") val userTag: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("avatar_dark_url") val avatarDarkUrl: String? = null,
    val role: Role? = null,
    val colour: String? = null,
    val deleted: Boolean = false,
    val system: Boolean = false,
    val topical: Boolean = false,
    @SerialName("ignored_by_me") val ignoredByMe: Boolean = false,
)

/** A role and its members (`GET /roles/{slug}`): a +role link's card (TV-22). */
@Serializable
data class RoleCard(
    val id: String? = null,
    val name: String,
    val slug: String? = null,
    val colour: String? = null,
    val members: List<Person> = emptyList(),
    @SerialName("members_count") val membersCount: Int = 0,
)

/** The signed-in member (`GET /me`): their card, email and rights. */
@Serializable
data class Me(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val username: String? = null,
    @SerialName("user_tag") val userTag: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("avatar_dark_url") val avatarDarkUrl: String? = null,
    val colour: String? = null,
    val roles: List<Role> = emptyList(),
    @SerialName("bio_md") val bioMd: String? = null,
    val email: String? = null,
    val verified: Boolean = false,
    /** The welcome card (About, Rules, roles) was seen (BR-07). */
    val welcomed: Boolean = false,
    /**
     * "Automatic punctuation" (ST-02). This and the member's other choices below are null
     * where the server does not send them (an older idejo): Profile and account hides their
     * rows instead of guessing.
     */
    @SerialName("auto_punctuation") val autoPunctuation: Boolean? = null,
    /** "Use the graphical composer" (CO-10, ST-02, IX-15); readers take null as on. */
    @SerialName("graphical_composer") val graphicalComposer: Boolean? = null,
    /** "Show message controls on mobile" (MA-01, IX-15); readers take null as off. */
    @SerialName("controls_on_mobile") val controlsOnMobile: Boolean? = null,
    @SerialName("controls_on_desktop") val controlsOnDesktop: Boolean? = null,
    /** "Members can send me private messages" (PC-03). */
    @SerialName("accepts_private_messages") val acceptsPrivateMessages: Boolean? = null,
    @SerialName("warning_pending") val warningPending: Boolean = false,
    val administrator: Boolean = false,
    val moderator: Boolean = false,
    @SerialName("server_moderator") val serverModerator: Boolean = false,
    /**
     * On a group's inbox, for a member linked to its provider: **Save for all forums** is
     * offered (ST-07, IX-17); false elsewhere and from older servers.
     */
    val everywhere: Boolean = false,
    /**
     * The onboarding tour (IX-18): the onboarding topic and the presentation question while
     * the member is on it; null once finished, and from servers without it.
     */
    val onboarding: Onboarding? = null,
)

/** `GET /me`'s `onboarding` (IX-18): where the tour leads and what it asks. */
@Serializable
data class Onboarding(
    @SerialName("topic_id") val topicId: String,
    @SerialName("topic_title") val topicTitle: String,
    /** The new user presentation question, asked once the first message is sent. */
    val question: String,
)

/** `POST /me/everywhere` (ST-07, IX-17): the inbox's [sections], saved on every forum. */
@Serializable
data class EverywhereRequest(
    val sections: List<String>,
)

/** What `POST /me/everywhere` answers: the forums' names, saved and missed. */
@Serializable
data class EverywhereResult(
    val saved: List<String> = emptyList(),
    val missed: List<String> = emptyList(),
)

/**
 * `PATCH /me`: only the fields set are sent (the API's JSON leaves nulls out), the others
 * kept as they are. The server answers with `GET /me`.
 */
@Serializable
data class MePatch(
    /** The display name (ST-01): never blank; the server says so if it is. */
    @SerialName("display_name") val displayName: String? = null,
    /** The user tag (ST-01): `""` clears it. */
    @SerialName("user_tag") val userTag: String? = null,
    /** The bio's Markdown (ST-01): `""` clears it. */
    @SerialName("bio_md") val bioMd: String? = null,
    @SerialName("auto_punctuation") val autoPunctuation: Boolean? = null,
    @SerialName("graphical_composer") val graphicalComposer: Boolean? = null,
    @SerialName("controls_on_mobile") val controlsOnMobile: Boolean? = null,
    @SerialName("controls_on_desktop") val controlsOnDesktop: Boolean? = null,
    @SerialName("accepts_private_messages") val acceptsPrivateMessages: Boolean? = null,
)
