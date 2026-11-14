// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.testing

import kotlinx.coroutines.runBlocking
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.ForumApi
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.model.ApiError
import org.idejo.app.core.model.Commit
import org.idejo.app.core.model.CommitResult
import org.idejo.app.core.model.MessageState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The API guide's section 6, the growing message, as its diagram shows it. */
class GuideComposerTest {
    private val fake = FakeIdejo()
    private val api = ForumApi(fake.address, "/api/v1", { token }, IdejoHttp.forLocalTesting())
    private val token = fake.tokenFor(FakePeople.Ada)

    @After
    fun stop() = fake.close()

    private fun commit(commit: Commit): CommitResult =
        runBlocking {
            (api.commit("t_1", commit) as ApiResult.Success).value
        }

    @Test
    fun `typing, then Enter opens, Enter appends, Ctrl+Enter closes held`() =
        runBlocking {
            val typing = (api.typing("t_1") as ApiResult.Success).value
            assertEquals(fake.clock.now, typing.typingSince)

            val opened = commit(Commit("Hi"))
            val message = requireNotNull(opened.message)
            assertEquals(MessageState.Open, message.state)
            // The server adds a full stop after unpunctuated text.
            assertEquals("Hi.", message.bodyMd)
            assertEquals(message.id, opened.composer.openMessageId)

            fake.clock.advanceSeconds(5)
            val appended = requireNotNull(commit(Commit("Hi. Hello")).message)
            assertEquals(message.id, appended.id)
            assertEquals("Hi. Hello.", appended.bodyMd)
            assertTrue(appended.cursor > message.cursor)

            val closed = commit(Commit("Hi. Hello", breakMessage = true))
            assertEquals(MessageState.Closed, closed.message?.state)
            assertTrue(closed.message!!.held)
            assertNull(closed.composer.openMessageId)
            assertEquals(Instant.parse("2026-11-14T09:41:00Z"), closed.composer.heldUntil)
        }

    @Test
    fun `a new message waits until held_until, then opens`() =
        runBlocking {
            commit(Commit("Hi", breakMessage = true))
            val refused =
                (
                    api.commit(
                        "t_1",
                        Commit("Again"),
                    ) as ApiResult.Failure
                ).problem as ApiProblem.Server
            assertEquals(409, refused.status)
            assertEquals(ApiError.TOO_SOON, refused.error.code)
            assertEquals(
                "You cannot post a new message yet. Append your old message.",
                refused.error.message,
            )

            val state = (api.composer("t_1") as ApiResult.Success).value
            assertEquals(state.heldUntil, state.mayStartNewAt)
            fake.clock.advanceSeconds(60)
            assertEquals(MessageState.Open, commit(Commit("Again")).message?.state)
        }

    @Test
    fun `a reply while a message is open closes it and starts the reply`() =
        runBlocking {
            val first = requireNotNull(commit(Commit("Hi")).message)
            val reply =
                requireNotNull(
                    commit(Commit("the reply", new = true, replyTo = listOf("m_1"))).message,
                )
            assertNotEquals(first.id, reply.id)
            assertEquals("Leo Moreau", reply.repliesTo.single().authorName)
            assertEquals(
                MessageState.Closed,
                fake.messages("t_1").first { it.id == first.id }.state,
            )
            assertFalse(fake.messages("t_1").first { it.id == first.id }.held)
        }

    @Test
    fun `without early replies, a reply waits for the timers`() =
        runBlocking {
            FakeIdejo(settings = FakeSettings(earlyReply = false)).use { strict ->
                val api =
                    ForumApi(strict.address, "/api/v1", { token2 }, IdejoHttp.forLocalTesting())
                token2 = strict.tokenFor(FakePeople.Ada)
                api.commit("t_1", Commit("Hi"))
                val refused =
                    (
                        api.commit(
                            "t_1",
                            Commit("reply", new = true),
                        ) as ApiResult.Failure
                    ).problem
                assertEquals(ApiError.TOO_SOON, (refused as ApiProblem.Server).error.code)
                strict.clock.advanceSeconds(60)
                assertTrue(api.commit("t_1", Commit("reply", new = true)) is ApiResult.Success)
            }
        }

    private var token2: org.idejo.app.core.model.AccessToken? = null

    @Test
    fun `catch-up returns every change since a cursor, oldest first`() =
        runBlocking {
            val window = (api.messages("t_1") as ApiResult.Success).value
            commit(Commit("Hi"))
            commit(Commit("Hi there", breakMessage = true))
            val changes = (api.changes("t_1", since = window.cursor) as ApiResult.Success).value
            assertEquals(listOf("Hi there."), changes.data.map { it.bodyMd })
            assertEquals(changes.data.single().cursor, changes.cursor)
            assertFalse(changes.more)

            val expired =
                (
                    api.changes(
                        "t_1",
                        since = 9_999,
                    ) as ApiResult.Failure
                ).problem as ApiProblem.Server
            assertEquals(410, expired.status)
            assertEquals(ApiError.CURSOR_EXPIRED, expired.error.code)
        }
}
