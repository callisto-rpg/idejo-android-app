// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.api

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import okhttp3.Request
import org.idejo.app.core.model.AccessToken
import org.idejo.app.core.model.ApiError
import org.idejo.app.core.model.Commit
import org.idejo.app.core.model.MentionState
import org.idejo.app.core.model.MessageState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ForumApiTest {
    private val server = MockWebServer()
    private val other = MockWebServer()
    private val http = IdejoHttp.forTesting()
    private var token: AccessToken? = AccessToken("idj_garden")
    private lateinit var api: ForumApi

    @Before
    fun start() {
        server.start()
        other.start()
        api = ForumApi(server.address(), "/api/v1", { token }, http)
    }

    @After
    fun stop() {
        server.close()
        other.close()
    }

    private fun <T> ApiResult<T>.value(): T = (this as ApiResult.Success).value

    private fun ApiResult<*>.server(): ApiProblem.Server =
        (this as ApiResult.Failure).problem as ApiProblem.Server

    @Test
    fun `SI-04 the token goes as Bearer, never in the address`() =
        runTest {
            server.enqueue(json(payload("composer.json")))
            api.composer("t_1").value()
            val sent = server.takeRequest()
            assertEquals("/api/v1/topics/t_1/composer", sent.url.encodedPath)
            assertEquals("Bearer idj_garden", sent.headers["Authorization"])
            assertFalse(sent.url.toString().contains("idj_"))
        }

    @Test
    fun `SY-04 a request to another host leaves without the token`() {
        val client =
            http
                .newBuilder()
                .addInterceptor(
                    BearerInterceptor(server.address(), {
                        token
                    }),
                ).build()
        other.enqueue(json("{}"))
        server.enqueue(json("{}"))
        client
            .newCall(
                Request
                    .Builder()
                    .url(
                        other.url("/x"),
                    ).header("Authorization", "Bearer idj_garden")
                    .build(),
            ).execute()
            .close()
        client.newCall(Request.Builder().url(server.url("/x")).build()).execute().close()
        assertNull(other.takeRequest().headers["Authorization"])
        assertEquals("Bearer idj_garden", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `SI-05 a visitor's requests carry no token`() =
        runTest {
            token = null
            server.enqueue(json("""{"data": []}"""))
            assertEquals(emptyList<Any>(), api.categories().value())
            assertNull(server.takeRequest().headers["Authorization"])
        }

    @Test
    fun `SY-07 a message and its window are read leniently`() =
        runTest {
            server.enqueue(json(payload("window.json")))
            val window = api.messages("t_1", firstUnread = true).value()
            assertEquals("first_unread=true", server.takeRequest().url.encodedQuery)

            val message = window.data.single()
            assertEquals(MessageState.Closed, message.state)
            assertEquals("Ada Lovelace", message.author.displayName)
            assertEquals("#6a3e8e", message.author.role?.colour)
            assertEquals(4182L, message.cursor)
            assertEquals(Instant.parse("2026-11-13T09:41:00Z"), message.pinnedAt)
            assertEquals(Instant.parse("2026-11-13T09:40:12.123456Z"), message.openedAt)
            assertEquals("user", message.mentions.single().type)
            assertEquals(1200, message.images.single().width)
            assertTrue(message.inlineSpoilers)
            assertEquals("Leo Moreau", message.repliesTo.single().authorName)
            assertNull(
                message.poll
                    ?.options
                    ?.first()
                    ?.count,
            )
            assertEquals("youtube", message.plugins.single().command)
            assertTrue(message.reactions.single().mine)

            val opening = requireNotNull(window.firstMessage)
            assertTrue(opening.author.topical)
            assertNull(opening.author.id)
            assertEquals(MessageState.Unknown, opening.state)
            assertEquals("m_1", window.firstUnread?.messageId)
            assertEquals("c_prev_signed", window.prevCursor)
            assertEquals(4182L, window.cursor)
        }

    @Test
    fun `BR-03 SY-07 topics come a page at a time, with previews`() =
        runTest {
            server.enqueue(json(payload("topics.json")))
            val page = api.categoryTopics("c_veg", cursor = "c_1", expanded = true).value()
            val query = server.takeRequest().url
            assertEquals("c_1", query.queryParameter("cursor"))
            assertEquals("true", query.queryParameter("expanded"))
            val topic = page.data.single()
            assertEquals(MentionState.None, topic.mentionState)
            assertEquals("Seeds", topic.tags.single().name)
            assertEquals(6, topic.preview?.writersCount)
            assertEquals("c_next_signed", page.nextCursor)
        }

    @Test
    fun `the Enter sends the whole open message`() =
        runTest {
            server.enqueue(
                json(
                    """{"message": ${payload(
                        "message.json",
                    )}, "composer": ${payload("composer.json")}}""",
                ),
            )
            val result = api.commit("t_1", Commit("Hi. Hello", breakMessage = true)).value()
            assertEquals("m_2", result.composer.openMessageId)
            val body = requireNotNull(server.takeRequest().body).utf8()
            assertEquals("""{"body_md":"Hi. Hello","break":true}""", body)
        }

    @Test
    fun `catch-up asks for changes since the last cursor`() =
        runTest {
            server.enqueue(
                json("""{"data": [${payload("message.json")}], "cursor": 4182, "more": false}"""),
            )
            val changes = api.changes("t_1", since = 4100).value()
            assertEquals("4100", server.takeRequest().url.queryParameter("since"))
            assertEquals(4182L, changes.cursor)
            assertFalse(changes.more)

            server.enqueue(error(410, ApiError.CURSOR_EXPIRED))
            assertEquals(
                ApiError.CURSOR_EXPIRED,
                api
                    .changes("t_1", since = 1)
                    .server()
                    .error.code,
            )
        }

    @Test
    fun `SI-09 SI-12 invalid_token means signed out`() =
        runTest {
            server.enqueue(error(401, ApiError.INVALID_TOKEN, "Sign in again."))
            assertTrue(api.me().server().signedOut)
            server.enqueue(error(401, ApiError.UNAUTHENTICATED))
            assertFalse(api.me().server().signedOut)
        }

    @Test
    fun `SI-10 banned and suspended carry the server's message and until`() =
        runTest {
            server.enqueue(
                error(
                    403,
                    ApiError.SUSPENDED,
                    "You are suspended until 1 December.",
                    """{"until": "2026-12-01T00:00:00Z"}""",
                ),
            )
            val problem = api.me().server()
            assertTrue(problem.barred)
            assertEquals("You are suspended until 1 December.", problem.error.message)
            assertEquals(Instant.parse("2026-12-01T00:00:00Z"), problem.error.until)

            server.enqueue(error(403, ApiError.FORBIDDEN))
            assertFalse(api.me().server().barred)
        }

    @Test
    fun `429 gives Retry-After, even without the API's error shape`() =
        runTest {
            server.enqueue(
                error(
                    429,
                    ApiError.RATE_LIMITED,
                    "Too many requests.",
                    "{}",
                    "Retry-After" to "12",
                ),
            )
            assertEquals(Duration.ofSeconds(12), api.server().server().retryAfter)

            server.enqueue(json("<html>slow down</html>", 429, "Retry-After" to "3"))
            val bare = api.server().server()
            assertEquals(ApiError.RATE_LIMITED, bare.error.code)
            assertEquals(Duration.ofSeconds(3), bare.retryAfter)
        }

    @Test
    fun `Retry-After as seconds or a date`() {
        val now = Instant.parse("2026-11-14T10:00:00Z")
        assertEquals(Duration.ofSeconds(30), RetryAfter.parse("30", now))
        assertEquals(Duration.ofSeconds(90), RetryAfter.parse("Sat, 14 Nov 2026 10:01:30 GMT", now))
        assertEquals(Duration.ZERO, RetryAfter.parse("Sat, 14 Nov 2026 09:00:00 GMT", now))
        assertNull(RetryAfter.parse("soon", now))
        assertNull(RetryAfter.parse(null, now))
    }

    @Test
    fun `422 lists a form's problems`() =
        runTest {
            server.enqueue(
                error(
                    422,
                    ApiError.VALIDATION,
                    "Check the form.",
                    """{"fields": {"title": ["is too long"]}}""",
                ),
            )
            assertEquals(
                mapOf("title" to listOf("is too long")),
                api
                    .topic("t_1")
                    .server()
                    .error.fields,
            )
        }

    @Test
    fun `no answer or an unreadable one`() =
        runTest {
            server.enqueue(json("{not json"))
            assertTrue((api.server() as ApiResult.Failure).problem is ApiProblem.Malformed)
            server.enqueue(json("<html>Bad gateway</html>", 502))
            assertTrue((api.server() as ApiResult.Failure).problem is ApiProblem.Malformed)
            server.close()
            assertTrue((api.server() as ApiResult.Failure).problem is ApiProblem.Network)
        }
}
