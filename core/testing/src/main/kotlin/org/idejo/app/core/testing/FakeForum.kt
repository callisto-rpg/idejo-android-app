// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.testing

import org.idejo.app.core.model.Category
import org.idejo.app.core.model.CategoryPermissions
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.MessageState
import org.idejo.app.core.model.Person
import org.idejo.app.core.model.Tag
import org.idejo.app.core.model.Topic
import java.time.Instant

/** How the fake forum is set up. */
data class FakeSettings(
    val name: String = "Garden club",
    /** `gm_window_seconds`: a message closed sooner is held until then. */
    val windowSeconds: Int = 60,
    /** `gm_unanswered_seconds`. */
    val unansweredSeconds: Int = 30,
    /** `gm_early_reply`: a reply may start before the timers run out. */
    val earlyReply: Boolean = true,
    val autoPunctuation: Boolean = true,
    /** `GET /server`'s theme (TH-01): the Garden sample's colours. */
    val theme: org.idejo.app.core.model.ServerTheme =
        org.idejo.app.core.model.ServerTheme(
            primary = "#3f6b3a",
            secondary = "#8a6d3b",
            tertiary = "#b5523b",
            lightBackground = "#f6f3ea",
            darkBackground = "#1d211b",
        ),
)

/** The people of the fake forum. */
object FakePeople {
    val Ada = Person(id = "u_ada", displayName = "Ada Lovelace", username = "ada", userTag = "ada")
    val Mina =
        Person(
            id = "u_mina",
            displayName = "Mina Kader",
            username = "mina",
            role =
                org.idejo.app.core.model
                    .Role("Moderator", colour = "#6a3e8e"),
            colour = "#6a3e8e",
        )
    val Leo = Person(id = "u_leo", displayName = "Leo Moreau", username = "leo")
    val all = listOf(Ada, Mina, Leo)
}

/** One topic's messages and change cursor, as the server keeps them. */
internal class TopicRecord(
    var topic: Topic,
    val messages: MutableList<Message> = mutableListOf(),
    var changeSeq: Long = 0,
)

/** What one member follows, muted and has not read, by topic id. */
internal class Reader(
    val followed: MutableSet<String> = linkedSetOf(),
    val muted: MutableSet<String> = mutableSetOf(),
    val unread: MutableMap<String, Unseen> = mutableMapOf(),
)

/** Unread messages in a topic and the strongest reason (SH-32). */
internal data class Unseen(
    val count: Int,
    val state: org.idejo.app.core.model.MentionState,
)

/** An author's composer in a topic (API guide §6). */
internal class ComposerRecord(
    var openMessageId: String? = null,
    var typingSince: Instant? = null,
    var heldUntil: Instant? = null,
    var lastOpenedAt: Instant? = null,
)

internal fun seedCategories(): List<Category> =
    listOf(
        Category(
            id = "c_veg",
            title = "Vegetables",
            slug = "vegetables",
            colour = "#8a6a1f",
            topicsCount = 1,
            tags = listOf(Tag("g_seeds", "Seeds", "seeds")),
            permissions =
                CategoryPermissions(
                    view = true,
                    createTopic = true,
                    post = true,
                    attach = true,
                    react = true,
                ),
        ),
        Category(
            id = "c_hidden",
            title = "Staff room",
            slug = "staff-room",
            permissions = CategoryPermissions(view = false),
        ),
    )

internal fun seedTopic(now: Instant): TopicRecord {
    val topic =
        Topic(
            id = "t_1",
            title = "Tomato plans for spring",
            slug = "tomato-plans-for-spring",
            categoryId = "c_veg",
            tags = listOf(Tag("g_seeds", "Seeds", "seeds")),
            author = FakePeople.Leo,
            createdAt = now,
            lastActivityAt = now,
            messagesCount = 1,
        )
    val opening =
        Message(
            id = "m_1",
            topicId = "t_1",
            author = FakePeople.Leo,
            state = MessageState.Closed,
            bodyMd = "Planning this year's beds.",
            cursor = 1,
            openedAt = now,
            closedAt = now,
            updatedAt = now,
        )
    return TopicRecord(topic, mutableListOf(opening), changeSeq = 1)
}
