// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.idejo.app.core.api.ForumApi
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.model.MessageState
import org.idejo.app.core.testing.FakeIdejo
import org.idejo.app.core.testing.FakePeople
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The composer against FakeIdejo, as the API guide's section 6 draws it. */
class ComposerFlowTest {
    private val fake = FakeIdejo()
    private val token = fake.tokenFor(FakePeople.Ada)
    private val api = ForumApi(fake.address, "/api/v1", { token }, IdejoHttp.forLocalTesting())

    @After
    fun stop() = fake.close()

    @Test
    fun `CO-02 typing, Enter, Enter, then Enter three times grows and completes a message`() =
        runTest {
            var now = 0L
            val composer = Composer(ApiComposerBackend.of(api, "t_1"), backgroundScope) { now }

            composer.typed("Hi")
            composer.state.first { it.server?.typingSince != null }
            composer.enter()
            val opened = composer.state.first { it.inFlight == null && it.sent.isNotEmpty() }
            assertEquals("Hi.", opened.sent)
            assertEquals("Hi. ", opened.text)
            val id = requireNotNull(opened.openMessageId)
            assertEquals(MessageState.Open, fake.messages("t_1").first { it.id == id }.state)

            composer.typed("Hi. Hello")
            now += 5_000
            composer.enter()
            composer.state.first { it.inFlight == null && it.sent == "Hi. Hello." }
            assertEquals("Hi. Hello.", fake.messages("t_1").first { it.id == id }.bodyMd)

            now += 5_000
            composer.edited("Hi. Hello. ", "Hi. Hello. \n")
            now += 300
            composer.enter()
            now += 300
            composer.enter()
            val done = composer.state.first { it.committedId != null }
            assertEquals(ComposerStatus.SentAndCommitted, done.status)
            assertEquals(MessageState.Closed, fake.messages("t_1").first { it.id == id }.state)
        }

    @Test
    fun `CO-08 too_soon shows the server's words and keeps the text`() =
        runTest {
            val composer = Composer(ApiComposerBackend.of(api, "t_1"), backgroundScope)
            composer.typed("Hi")
            composer.complete()
            composer.state.first { it.committedId != null }

            composer.typed("Again")
            composer.enter()
            val refused = composer.state.first { it.error != null }
            val error = (refused.error as ComposerError.Refused).error
            assertEquals(
                "You cannot post a new message yet. Append your old message.",
                error.message,
            )
            assertEquals("Again", refused.text)
            assertTrue(refused.hasUnsent)
        }

    @Test
    fun `GM-08 the composer finds the author's open message`() =
        runTest {
            val first = Composer(ApiComposerBackend.of(api, "t_1"), backgroundScope)
            first.typed("Hi")
            first.enter()
            first.state.first { it.openMessageId != null }

            val later = Composer(ApiComposerBackend.of(api, "t_1"), backgroundScope)
            later.refresh()
            val resumed = later.state.first { it.openMessageId != null }
            assertEquals("Hi.", resumed.sent)
            assertEquals(ComposerStatus.Sent, resumed.status)
        }

    @Test
    fun `CO-21 leaving the topic never closes the open message from the app`() =
        runTest {
            val scope =
                kotlinx.coroutines.CoroutineScope(coroutineContext + kotlinx.coroutines.Job())
            val composer = Composer(ApiComposerBackend.of(api, "t_1"), scope)
            composer.typed("Hi")
            composer.enter()
            val id = requireNotNull(composer.state.first { it.openMessageId != null }.openMessageId)
            scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
            // The server closes it after 30 s without the channel (invariant 7), not the app.
            assertEquals(MessageState.Open, fake.messages("t_1").first { it.id == id }.state)
        }

    @Test
    fun `CO-18 an edit is saved with PUT, and ending it brings the composer back`() =
        runTest {
            val composer = Composer(ApiComposerBackend.of(api, "t_1"), backgroundScope)
            composer.typed("Hi")
            composer.complete()
            composer.state.first { it.committedId != null }
            val closed = fake.messages("t_1").last()

            composer.edit(closed)
            composer.typed("Hi there")
            composer.complete()
            composer.state.first { it.editing == null }
            assertEquals("Hi there", fake.messages("t_1").last().bodyMd)
        }

    @Test
    fun `CO-18 an emptied edit deletes the message once confirmed`() =
        runTest {
            val composer = Composer(ApiComposerBackend.of(api, "t_1"), backgroundScope)
            composer.typed("Oops")
            composer.complete()
            composer.state.first { it.committedId != null }
            val closed = fake.messages("t_1").last()

            composer.edit(closed)
            composer.typed("")
            composer.enter()
            composer.state.first { it.confirmDelete }
            composer.confirmDelete()
            composer.state.first { it.editing == null }
            assertEquals(MessageState.Deleted, fake.messages("t_1").last().state)
        }

    @Test
    fun `CO-17 the forward card's targets, the topic left out`() =
        runTest {
            val all = (api.forwardTargets() as org.idejo.app.core.api.ApiResult.Success).value
            val others =
                (
                    api.forwardTargets(
                        except = "t_1",
                    ) as org.idejo.app.core.api.ApiResult.Success
                ).value
            assertTrue(all.any { it.id == "t_1" })
            assertTrue(others.none { it.id == "t_1" })
        }
}
