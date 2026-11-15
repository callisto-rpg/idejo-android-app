// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.api

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.idejo.app.core.model.GifConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GifsTest {
    private val server = MockWebServer()
    private val config = GifConfig("klipy", "key1", "low", "Search KLIPY", "KLIPY")

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun klipy() = Klipy(IdejoHttp.forLocalTesting(), server.url("/api/v1").toString())

    @Test
    fun `CO-15 a search asks the provider itself, ads left out, WebP first, its order kept`() =
        runTest {
            server.enqueue(json(PAGE))
            val page = klipy().page(config, "cat", 2)!!
            assertEquals(listOf("1", "3"), page.items.map { it.id })
            assertEquals("https://static.klipy.com/1-sm.webp", page.items[0].preview)
            assertEquals("https://static.klipy.com/1-md.gif", page.items[0].full)
            assertTrue(page.more)
            val request = server.takeRequest()
            assertEquals("/api/v1/key1/gifs/search", request.url.encodedPath)
            assertEquals("cat", request.url.queryParameter("q"))
            assertEquals("low", request.url.queryParameter("content_filter"))
            assertEquals("2", request.url.queryParameter("page"))
            assertNull(request.headers["Authorization"])
        }

    @Test
    fun `CO-15 without a query the popular ones, and a failure is null`() =
        runTest {
            server.enqueue(json(PAGE))
            klipy().page(config, null, 1)
            assertEquals("/api/v1/key1/gifs/trending", server.takeRequest().url.encodedPath)
            server.enqueue(mockwebserver3.MockResponse(code = 500))
            assertNull(klipy().page(config, null, 1))
        }

    @Test
    fun `CO-15 only a provider the app knows is called`() {
        assertNull(GifSearch.of(IdejoHttp.forLocalTesting(), "other"))
        assertTrue(GifSearch.of(IdejoHttp.forLocalTesting(), "klipy") is Klipy)
    }
}

private val PAGE =
    """
    {"result": true, "data": {"has_next": true, "data": [
      {"id": 1, "slug": "cat-1", "title": "Cat", "type": "gif", "file": {
        "sm": {"webp": {"url": "https://static.klipy.com/1-sm.webp"}},
        "md": {"gif": {"url": "https://static.klipy.com/1-md.gif"}}}},
      {"id": 2, "type": "ad", "file": {"sm": {"gif": {"url": "https://ads/x.gif"}}}},
      {"id": 3, "slug": "cat-3", "title": "", "type": "gif", "file": {
        "xs": {"gif": {"url": "https://static.klipy.com/3-xs.gif"}}}}
    ]}}
    """.trimIndent()
