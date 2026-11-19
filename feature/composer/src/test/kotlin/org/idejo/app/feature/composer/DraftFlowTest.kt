// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.idejo.app.core.api.ForumApi
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.data.Drafts
import org.idejo.app.core.testing.FakeIdejo
import org.idejo.app.core.testing.FakePeople
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Drafts (OF-03): the composer's and the new-topic form's unsent text, after a kill. */
class DraftFlowTest {
    @get:Rule val folder = TemporaryFolder()

    private val fake = FakeIdejo()
    private val token = fake.tokenFor(FakePeople.Ada)
    private val http = IdejoHttp.forLocalTesting()
    private val api = ForumApi(fake.address, "/api/v1", { token }, http)
    private val origin = fake.address.toString()
    private val place = Drafts.topic("t_1")

    @After
    fun stop() = fake.close()

    /** Waits off the test thread, while the composer's own work runs. */
    private suspend fun until(done: () -> Boolean) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.withTimeout(5_000) {
                while (!done()) kotlinx.coroutines.delay(10)
            }
        }

    private fun composer(
        drafts: Drafts,
        scope: kotlinx.coroutines.CoroutineScope,
    ) = Composer(
        ApiComposerBackend.of(api, "t_1"),
        scope,
        draft = drafts.slot(origin, place),
    ).also { it.refresh() }

    @Test
    fun `OF-03 text never sent comes back in a new app, without typing on the server`() =
        runTest {
            val drafts = Drafts(folder.root)
            val first = composer(drafts, backgroundScope)
            first.state.first { it.server != null }
            first.typed("Half a thought")
            until { drafts.get(origin, place) == "Half a thought" }
            drafts.settle()

            val again = composer(Drafts(folder.root), backgroundScope)
            val back = again.state.first { it.text == "Half a thought" }
            assertEquals(ComposerStatus.Unsent, back.status)
            assertNull(back.openMessageId)
        }

    @Test
    fun `OF-03 the draft goes after the open message, and leaves once sent`() =
        runTest {
            val drafts = Drafts(folder.root)
            val first = composer(drafts, backgroundScope)
            first.state.first { it.server != null }
            first.typed("Hi")
            first.enter()
            val open = first.state.first { it.inFlight == null && it.sent.isNotEmpty() }
            first.typed(open.text + "and more")
            until { drafts.get(origin, place) == " and more" }
            drafts.settle()

            val reopened = Drafts(folder.root)
            val again = composer(reopened, backgroundScope)
            val back = again.state.first { it.text.endsWith("and more") }
            assertEquals("Hi. and more", back.text)
            assertEquals(open.openMessageId, back.openMessageId)

            again.complete()
            again.state.first { it.committedId != null }
            until { reopened.get(origin, place) == null }
        }

    @Test
    fun `OF-03 the new-topic form keeps its title and first message until started`() =
        runTest {
            val drafts = Drafts(folder.root)
            val slot = drafts.slot(origin, Drafts.newTopic("c_veg"))
            val form =
                NewTopic(
                    ApiNewTopicBackend.of(api),
                    backgroundScope,
                    NewTopicForm(categoryId = "c_veg"),
                    slot,
                )
            form.change { it.copy(title = "Seeds", body = "Which ones?") }
            until { slot.read() != null }
            drafts.settle()

            val reopened =
                NewTopic(
                    ApiNewTopicBackend.of(api),
                    backgroundScope,
                    NewTopicForm(categoryId = "c_veg"),
                    Drafts(folder.root).slot(origin, Drafts.newTopic("c_veg")),
                )
            assertEquals("Seeds", reopened.form.value.title)
            assertEquals("Which ones?", reopened.form.value.body)
        }
}
