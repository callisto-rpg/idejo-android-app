// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The member's notification choices (`GET` and `PATCH /me/notification-settings`, ST-03,
 * NT-08): the email summary's and the push types'. A `PATCH` sends only what changed, so a
 * null field is left as it is.
 */
@Serializable
data class NotificationSettings(
    val email: Email? = null,
    val push: Push? = null,
) {
    /** What the email summary counts (NO-04), and whether it comes once a week. */
    @Serializable
    data class Email(
        val mentions: Boolean? = null,
        @SerialName("role_mentions") val roleMentions: Boolean? = null,
        @SerialName("own_topics") val ownTopics: Boolean? = null,
        @SerialName("followed_topics") val followedTopics: Boolean? = null,
        @SerialName("new_topics") val newTopics: Boolean? = null,
        @SerialName("all_activity") val allActivity: Boolean? = null,
        @SerialName("once_a_week") val onceAWeek: Boolean? = null,
    )

    /** What sends a push, for every browser and device of the member (PN-11). */
    @Serializable
    data class Push(
        val reply: Boolean? = null,
        val mention: Boolean? = null,
        @SerialName("role_mention") val roleMention: Boolean? = null,
        val followed: Boolean? = null,
        @SerialName("private") val privateMessages: Boolean? = null,
    )
}

/** A browser or device taking push (`GET /me/push`, NT-02, NT-08). */
@Serializable
data class PushRegistration(
    val id: String,
    /** `unifiedpush` or `web`. */
    val kind: String,
    val name: String? = null,
    @SerialName("endpoint_host") val endpointHost: String? = null,
    /** This device: the one that asks. */
    @SerialName("this_device") val thisDevice: Boolean = false,
    @SerialName("created_at") val createdAt: Time? = null,
    @SerialName("last_success_at") val lastSuccessAt: Time? = null,
)

/** `POST /me/push/test` (NT-08): how many devices a test notification was queued for. */
@Serializable
data class PushTest(
    val queued: Int = 0,
)
