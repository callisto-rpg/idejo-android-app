// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.inbox

import org.idejo.app.core.data.ForumTopic
import org.idejo.app.core.data.InboxTopicsState
import org.idejo.app.core.model.Me
import org.idejo.app.core.model.Person
import org.idejo.app.core.model.Topic
import java.time.Instant

/** The inbox's samples: three followed topics of two forums, one forum unreachable. */
object InboxSamples {
    const val GARDEN = "https://garden.example.org/"
    const val TENNIS = "https://tennis.example.org/"
    private val ada = Person("u_ada", "Ada Lovelace")

    val tomatoes =
        ForumTopic(
            GARDEN,
            "Garden",
            null,
            Topic(
                "t_1",
                "Tomato plans",
                author = ada,
                messagesCount = 12,
                unreadCount = 3,
                mentionsCount = 1,
                lastActivityAt = hoursAgo(2),
            ),
        )
    val doubles =
        ForumTopic(
            TENNIS,
            "Tennis",
            null,
            Topic(
                "t_9",
                "Saturday doubles",
                author = Person("u_leo", "Leo"),
                messagesCount = 30,
                unreadCount = 5,
                reactionsCount = 2,
                lastActivityAt = hoursAgo(5),
            ),
        )
    val quiet =
        ForumTopic(
            GARDEN,
            "Garden",
            null,
            Topic(
                "t_2",
                "Seed swap",
                author = ada,
                messagesCount = 4,
                lastActivityAt = hoursAgo(30),
            ),
        )
    val state =
        InboxState(
            name = "idejo",
            me = Me("u_ada", "Ada Lovelace"),
            topics =
                InboxTopicsState(
                    topics = listOf(tomatoes, doubles, quiet),
                    unreachable = listOf("Chess club"),
                    loading = false,
                ),
            forums = mapOf(GARDEN to "Garden", TENNIS to "Tennis"),
        )

    /** Times relative to now, as the rows show them ("2 hr. ago"). */
    private fun hoursAgo(hours: Long): Instant = Instant.now().minusSeconds(hours * 3600)
}
