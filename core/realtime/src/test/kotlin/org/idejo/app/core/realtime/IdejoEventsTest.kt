// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.realtime

import org.idejo.app.core.model.MessageState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class IdejoEventsTest {
    private fun topic(
        event: String,
        payload: String,
    ) = IdejoEvents.parse(ChannelKind.Topic, event, json(payload))

    private val message = """{"id": "m_1", "topic_id": "t_1", "author": {"id": "u_ada", "display_name": "Ada"},
        "state": "closed", "body_md": "Hi.", "cursor": 9, "a_later_field": 1}"""
    private val topic = """{"id": "t_1", "title": "Tomato plans", "messages_count": 3}"""

    @Test
    fun `SY-07 message events carry the whole message and its cursor`() {
        val event =
            topic(
                "message.closed",
                """{"message": $message, "cursor": 9}""",
            ) as IdejoEvent.MessageChanged
        assertEquals(IdejoEvent.Change.Closed, event.change)
        assertEquals(MessageState.Closed, event.message.state)
        assertEquals(9L, event.cursor)
        for (name in listOf("opened", "appended", "edited", "deleted")) {
            assertTrue(
                topic("message.$name", """{"message": $message}""") is IdejoEvent.MessageChanged,
            )
        }
    }

    @Test
    fun `topic channel events`() {
        assertEquals(
            "m_1",
            (
                topic(
                    "message.forked",
                    """{"message": $message}""",
                ) as IdejoEvent.MessageForked
            ).message.id,
        )
        val reactions =
            topic(
                "reaction.changed",
                """{"message_id": "m_1", "reactions": [{"emoji": ":tada:", "name": "tada", "count": 2}]}""",
            )
        assertEquals(2, (reactions as IdejoEvent.ReactionsChanged).reactions.single().count)
        assertEquals(
            IdejoEvent.PollUpdated("m_1", null),
            topic("poll.updated", """{"message_id": "m_1", "poll": null}"""),
        )
        assertEquals(
            "Tomato plans",
            (topic("topic.moved", topic) as IdejoEvent.TopicChanged).topic.title,
        )
        assertEquals(
            IdejoEvent.MessagesMoved("t_1"),
            topic("messages.moved", """{"topic_id": "t_1"}"""),
        )
        assertEquals(
            IdejoEvent.Forbidden("forbidden"),
            topic("forbidden", """{"reason": "forbidden"}"""),
        )
    }

    @Test
    fun `category channel events, where topic_moved means it left`() {
        val parse = {
            event: String,
            payload: String,
            ->
            IdejoEvents.parse(ChannelKind.of("category:c_1"), event, json(payload))
        }
        assertEquals(
            3,
            (parse("topic.created", topic) as IdejoEvent.TopicCreated).topic.messagesCount,
        )
        assertEquals(
            IdejoEvent.TopicActivity("t_1", Instant.parse("2026-11-14T10:00:00Z"), 4),
            parse(
                "topic.activity",
                """{"topic_id": "t_1", "last_activity_at": "2026-11-14T10:00:00Z", "messages_count": 4}""",
            ),
        )
        assertEquals(
            IdejoEvent.TopicMovedOut("t_1", "c_1", "c_2"),
            parse("topic.moved", """{"topic_id": "t_1", "from": "c_1", "to": "c_2"}"""),
        )
        assertEquals(
            IdejoEvent.TopicArchived("t_1"),
            parse("topic.archived", """{"topic_id": "t_1"}"""),
        )
    }

    @Test
    fun `SI-09 user channel events`() {
        val parse = {
            event: String,
            payload: String,
            ->
            IdejoEvents.parse(ChannelKind.of("user:me"), event, json(payload))
        }
        assertEquals(
            IdejoEvent.TokenRevoked("tok_1"),
            parse("token.revoked", """{"token_id": "tok_1"}"""),
        )
        assertEquals(
            IdejoEvent.UnreadChanged(3, "mention"),
            parse("unread.changed", """{"count": 3, "level": "mention"}"""),
        )
        val note =
            parse(
                "notification",
                """{"kind": "mention", "message_id": "m_1", "topic_title": "Seeds", "author_name": "Mina"}""",
            )
        assertEquals("Mina", (note as IdejoEvent.Notification).authorName)
        assertEquals(
            IdejoEvent.ConversationUpdated("p_1"),
            parse("conversation.updated", """{"conversation_id": "p_1"}"""),
        )
        assertEquals(
            IdejoEvent.ScheduledPosted("m_1", "t_1"),
            parse("scheduled.posted", """{"message_id": "m_1", "topic_id": "t_1"}"""),
        )
        assertEquals(
            IdejoEvent.SettingsChanged(listOf("theme")),
            parse("settings.changed", """{"keys": ["theme"]}"""),
        )
    }

    @Test
    fun `SY-07 unknown events and unreadable payloads are Unknown`() {
        assertTrue(topic("message.levitated", "{}") is IdejoEvent.Unknown)
        assertTrue(
            topic("message.closed", """{"message": "not a message"}""") is IdejoEvent.Unknown,
        )
        assertTrue(topic("messages.moved", "[]") is IdejoEvent.Unknown)
    }
}
