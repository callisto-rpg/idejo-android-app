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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The composer against FakeIdejo, as the API guide's section 6 draws it. */
class ComposerFlowTest {
    private val fake = FakeIdejo()
    private val token = fake.tokenFor(FakePeople.Ada)
    private val http = IdejoHttp.forLocalTesting()
    private val api = ForumApi(fake.address, "/api/v1", { token }, http)

    @After
    fun stop() = fake.close()

    @Test
    fun `CO-02 typing, Enter, Enter, then Enter three times grows and completes a message`() =
        runTest {
            var now = 0L
            val composer =
                Composer(ApiComposerBackend.of(api, "t_1"), backgroundScope, clock = { now })

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

    @Test
    fun `CO-12 suggestions and emoji come from the forum`() =
        runTest {
            val composer = Composer(ApiComposerBackend.of(api, "t_1"), backgroundScope)
            assertEquals(listOf("ada"), composer.suggestions('@', "ad").map { it.insert })
            assertEquals(listOf("tada"), composer.emoji("tad").map { it.name })
        }

    @Test
    fun `CO-13 the commands of the topic`() =
        runTest {
            val composer = Composer(ApiComposerBackend.of(api, "t_1"), backgroundScope)
            val names = composer.commands().map { it.name }
            assertTrue(listOf("poll", "youtube", "gif", "new-topic").all { it in names })
        }

    @Test
    fun `CO-15 GIFs only while the forum has them on, with a provider the app knows`() =
        runTest {
            val off = Composer(ApiComposerBackend.of(api, "t_1"), backgroundScope) { null }
            assertNull(off.gifConfig())
            val config =
                org.idejo.app.core.model
                    .GifConfig("klipy", "k", name = "KLIPY")
            FakeIdejo(
                settings =
                    org.idejo.app.core.testing
                        .FakeSettings(gifs = config),
            ).use {
                val other = ForumApi(it.address, "/api/v1", { it.tokenFor(FakePeople.Ada) }, http)
                val known = { _: String? ->
                    org.idejo.app.core.api
                        .Klipy(http)
                }
                val on =
                    Composer(
                        ApiComposerBackend.of(other, "t_1"),
                        backgroundScope,
                        gifSearch = known,
                    )
                assertEquals("KLIPY", on.gifConfig()?.name)
                val unknown =
                    Composer(ApiComposerBackend.of(other, "t_1"), backgroundScope) { null }
                assertNull(unknown.gifConfig())
            }
        }

    @Test
    fun `CO-19 new topic starts from the open message, what is new sent first`() =
        runTest {
            val composer = Composer(ApiComposerBackend.of(api, "t_1"), backgroundScope)
            composer.typed("Seeds deserve their own topic")
            var start: NewTopicStart? = null
            composer.newTopic(null) { start = it }
            val id = composer.state.first { it.openMessageId != null }.openMessageId
            val started = waitFor { start }
            assertEquals("c_veg", started.categoryId)
            assertEquals(id, started.from)
            assertEquals("Seeds deserve their own topic.", started.body)
        }

    @Test
    fun `CO-19 a topic is created, continuing from a message, or waits for approval`() =
        runTest {
            val composer = Composer(ApiComposerBackend.of(api, "t_1"), backgroundScope)
            composer.typed("Seeds")
            composer.complete()
            val from = requireNotNull(composer.state.first { it.committedId != null }.committedId)
            val newTopic =
                NewTopic(
                    ApiNewTopicBackend.of(api),
                    backgroundScope,
                    NewTopicForm(categoryId = "c_veg", from = from, body = "Seeds."),
                )
            newTopic.form.first { it.categories.isNotEmpty() }
            newTopic.change { it.copy(title = "Seeds") }
            assertTrue(!newTopic.form.value.ready)
            newTopic.change { it.copy(tagIds = setOf("g_seeds")) }
            newTopic.create()
            val created = newTopic.form.first { it.created != null }.created!!
            val topic = (api.topic(created) as org.idejo.app.core.api.ApiResult.Success).value
            assertEquals(from, topic.continuesFrom?.messageId)

            fake.addCategory(
                org.idejo.app.core.model.Category(
                    "c_new",
                    "Newcomers",
                    "newcomers",
                    newTopics = "approval",
                    permissions =
                        org.idejo.app.core.model
                            .CategoryPermissions(createTopic = true),
                ),
            )
            val waiting =
                NewTopic(
                    ApiNewTopicBackend.of(api),
                    backgroundScope,
                    NewTopicForm(categoryId = "c_new", title = "Hello", body = "Hi."),
                )
            waiting.form.first { it.ready }
            waiting.create()
            assertEquals("Hello", waiting.form.first { it.pending != null }.pending)
        }

    @Test
    fun `CO-13 a poll goes under the message, what is new sent first, emoji and colours kept`() =
        runTest {
            val composer = Composer(ApiComposerBackend.of(api, "t_1"), backgroundScope)
            composer.typed("Which tomato")
            composer.poll(
                org.idejo.app.core.model
                    .PollCreate(
                        "Which?",
                        listOf(
                            org.idejo.app.core.model
                                .PollAnswer("Sungold", "tada", "#fdd835"),
                            org.idejo.app.core.model
                                .PollAnswer("Brandywine"),
                        ),
                        2.0,
                    ),
            )
            val id = composer.state.first { it.openMessageId != null }.openMessageId
            waitFor { fake.messages("t_1").firstOrNull { it.id == id && it.poll != null } }.let {
                assertEquals("Which tomato.", it.bodyMd)
                assertEquals(listOf("Sungold", "Brandywine"), it.poll!!.options.map { o -> o.text })
                assertEquals("tada", it.poll!!.options[0].emoji)
                assertEquals("#fdd835", it.poll!!.options[0].colour)
            }
        }

    @Test
    fun `CO-13 spoiler hides the message, again shows it`() =
        runTest {
            val composer = Composer(ApiComposerBackend.of(api, "t_1"), backgroundScope)
            composer.spoiler()
            assertEquals(
                ComposerError.NothingToHide,
                composer.state.first { it.error != null }.error,
            )
            composer.errorSeen()
            composer.typed("The end")
            composer.spoiler("Book 3")
            val id = composer.state.first { it.openMessageId != null }.openMessageId
            val hidden = waitFor { fake.messages("t_1").first { it.id == id }.spoiler }
            assertEquals("Book 3", hidden.overlay)
            composer.spoiler()
            assertNull(
                waitFor {
                    fake.messages("t_1").first { it.id == id }.takeIf {
                        it.spoiler ==
                            null
                    }
                }.spoiler,
            )
        }

    @Test
    fun `CO-20 a scheduled message takes the unsent text, listed until cancelled`() =
        runTest {
            val composer = Composer(ApiComposerBackend.of(api, "t_1"), backgroundScope)
            composer.typed("Water at dawn")
            composer.schedule(0.5)
            val listed = composer.scheduled.first { it.isNotEmpty() }.single()
            assertEquals("Water at dawn", listed.bodyMd)
            assertEquals("", composer.state.value.text)
            composer.cancelScheduled(listed.id)
            composer.scheduled.first { it.isEmpty() }
        }

    @Test
    fun `CO-14 an image is uploaded and its Markdown comes back, within the forum's limits`() =
        runTest {
            val composer = Composer(ApiComposerBackend.of(api, "t_1"), backgroundScope)
            val limits = composer.uploadLimits()
            assertEquals(org.idejo.app.core.testing.FAKE_UPLOAD_MAX_BYTES, limits.maxBytes)
            assertTrue("png" in limits.types)
            composer.imageRefused()
            assertEquals(
                ComposerError.ImageRefused,
                composer.state.first { it.error != null }.error,
            )
            composer.errorSeen()
            val upload = composer.upload(byteArrayOf(1, 2, 3), "image.png", "image/png")
            assertTrue(upload!!.markdown.startsWith("![image](/uploads/"))
        }

    @Test
    fun `CO-13 a plugin's block goes under the message`() =
        runTest {
            val composer = Composer(ApiComposerBackend.of(api, "t_1"), backgroundScope)
            composer.typed("Watch")
            composer.plugin("youtube", mapOf("link" to "https://youtu.be/x"))
            val id = composer.state.first { it.openMessageId != null }.openMessageId
            val block =
                waitFor {
                    fake
                        .messages("t_1")
                        .first { it.id == id }
                        .plugins
                        .firstOrNull()
                }
            assertEquals("youtube", block.command)
        }

    /** What [read] gives once it is there, the fake answering on its own thread. */
    private suspend fun <T : Any> waitFor(read: () -> T?): T {
        repeat(WAITS) {
            read()?.let { return it }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                kotlinx.coroutines.delay(WAIT_MILLIS)
            }
        }
        return requireNotNull(read())
    }
}

private const val WAITS = 50
private const val WAIT_MILLIS = 20L
