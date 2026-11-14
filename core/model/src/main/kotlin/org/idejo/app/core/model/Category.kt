// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Tag(
    val id: String,
    val name: String,
    val slug: String? = null,
)

@Serializable
data class CategoryGroup(
    val id: String,
    val name: String,
    val position: Int = 0,
)

/** What the viewer may do in a category. */
@Serializable
data class CategoryPermissions(
    val view: Boolean = true,
    @SerialName("create_topic") val createTopic: Boolean = false,
    val post: Boolean = false,
    val attach: Boolean = false,
    val react: Boolean = false,
    val tag: Boolean = false,
    val moderate: Boolean = false,
)

@Serializable
data class Category(
    val id: String,
    val title: String,
    val slug: String,
    val uri: String? = null,
    val url: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val colour: String? = null,
    val group: CategoryGroup? = null,
    val position: Int = 0,
    val archive: Boolean = false,
    @SerialName("topic_type") val topicType: String? = null,
    @SerialName("new_topics") val newTopics: String? = null,
    @SerialName("topics_count") val topicsCount: Int = 0,
    @SerialName("last_activity_at") val lastActivityAt: Time? = null,
    val tags: List<Tag> = emptyList(),
    val permissions: CategoryPermissions = CategoryPermissions(),
)
