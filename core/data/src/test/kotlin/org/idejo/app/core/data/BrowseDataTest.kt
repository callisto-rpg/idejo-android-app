// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.DiscoveryClient
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.model.TokenGrant
import org.idejo.app.core.model.Topic
import org.idejo.app.core.testing.FakeIdejo
import org.idejo.app.core.testing.FakePeople
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator
import kotlin.time.Duration.Companion.seconds

/** The home page and paged topic lists against the fake forum (BR-01 to BR-03, BR-10). */
class BrowseDataTest {
    @get:Rule val folder = TemporaryFolder()

    private val fake = FakeIdejo()
    private val http = IdejoHttp.forLocalTesting()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val sessions by lazy {
        SessionManager(
            ForumStore.create(folder.root.resolve("app.json"), scope),
            TokenVault(AesGcmBox { key }),
            http,
            scope,
            Dispatchers.IO,
        ) { ForumAddress.forLocalTesting(it.toHttpUrl()) }
    }

    @After
    fun stop() {
        fake.close()
        scope.cancel()
    }

    private fun signedIn(): ForumSession =
        runBlocking {
            val found = DiscoveryClient(http).discover(fake.address) as DiscoveryResult.Found
            val grant =
                TokenGrant(fake.tokenFor(FakePeople.Ada), tokenId = "tok_1", user = FakePeople.Ada)
            sessions.add(found, grant)
            requireNotNull(sessions.active)
        }

    private suspend fun <T> StateFlow<T>.until(done: (T) -> Boolean): T =
        withTimeout(5.seconds) { first(done) }

    private fun categoryPages(
        session: ForumSession,
        size: Int,
    ) = Pages<Topic>(scope, key = { it.id }) { cursor ->
        session.call { categoryTopics("c_veg", cursor, limit = size) }
    }

    @Test
    fun `BR-10 a list is read a page at a time, to its end`() =
        runBlocking {
            repeat(4) { fake.addTopic("Topic $it", "c_veg") }
            val pages = categoryPages(signedIn(), size = 2)

            pages.refresh()
            val first = pages.state.until { !it.loading }
            assertEquals(2, first.items.size)
            assertFalse(first.end)

            pages.more()
            pages.state.until { it.items.size == 4 && !it.loadingMore }
            pages.more()
            val last = pages.state.until { it.end }
            assertEquals(5, last.items.size)
            assertEquals(last.items.map { it.id }.distinct(), last.items.map { it.id })
        }

    @Test
    fun `BR-10 pull to refresh keeps the list shown until the new first page comes`() =
        runBlocking {
            val pages = categoryPages(signedIn(), size = 10)
            pages.refresh()
            pages.state.until { !it.loading }
            fake.addTopic("Peppers indoors", "c_veg")

            pages.refresh()
            assertTrue(pages.state.value.refreshing)
            assertEquals(1, pages.state.value.items.size)
            val again = pages.state.until { !it.refreshing }
            assertEquals(2, again.items.size)
        }

    @Test
    fun `BR-10 a failed page keeps what was shown, with why`() =
        runBlocking {
            val session = signedIn()
            val pages =
                Pages<Topic>(scope, key = { it.id }) { cursor ->
                    session.call { categoryTopics("c_none", cursor) }
                }
            pages.refresh()
            val failed = pages.state.until { it.problem != null }
            assertTrue(failed.problem is ApiProblem.Server)
            assertFalse(failed.loading)
        }

    @Test
    fun `BR-01 BR-03 home holds the categories and the active topics, with previews when asked`() =
        runBlocking {
            val feed = HomeFeed(signedIn(), scope)

            feed.refresh(expanded = false)
            val compact = feed.state.until { !it.loading }
            assertEquals(
                listOf("Vegetables"),
                compact.sections.flatMap { s ->
                    s.categories.map { it.category.title }
                },
            )
            assertEquals(listOf("t_1"), compact.active.map { it.id })
            assertNull(compact.active.single().preview)
            assertEquals("Vegetables", compact.category("c_veg")?.title)

            feed.refresh(expanded = true)
            val expanded = feed.state.until { it.expanded && !it.refreshing }
            assertTrue(
                expanded.active
                    .single()
                    .preview
                    ?.firstExcerpt
                    ?.isNotBlank() == true,
            )
        }
}
