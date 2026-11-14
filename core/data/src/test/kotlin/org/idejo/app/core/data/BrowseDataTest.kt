// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.DiscoveryClient
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.api.getOrNull
import org.idejo.app.core.model.Tag
import org.idejo.app.core.model.TokenGrant
import org.idejo.app.core.model.Topic
import org.idejo.app.core.realtime.SocketState
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
        sessions.active?.socket?.disconnect()
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

    @Test
    fun `BR-04 Filter by tag lists the topics carrying all the tags, or any with Or`() =
        runBlocking {
            val seeds = Tag("g_seeds", "Seeds", "seeds")
            val indoor = Tag("g_indoor", "Indoor", "indoor")
            fake.addTopic("Peppers indoors", tags = listOf(seeds, indoor))
            fake.addTopic("Seed swap", tags = listOf(seeds))
            fake.addTopic("Grow lights", tags = listOf(indoor))
            val session = signedIn()

            val tags = (session.call { tags() } as ApiResult.Success).value
            assertEquals(setOf("seeds", "indoor"), tags.mapNotNull { it.slug }.toSet())
            val all = session.call { tagged(listOf("seeds", "indoor")) }.getOrNull()
            assertEquals(listOf("Peppers indoors"), all?.data?.map { it.title })
            val any = session.call { tagged(listOf("seeds", "indoor"), any = true) }.getOrNull()
            assertEquals(
                setOf("Tomato plans for spring", "Peppers indoors", "Seed swap", "Grow lights"),
                any?.data?.map { it.title }?.toSet(),
            )
        }

    @Test
    fun `BR-05 search finds messages and marks the matches`() =
        runBlocking {
            fake.post(FakePeople.Mina, "t_1", "Sungold every year")
            val session = signedIn()

            val hits =
                session
                    .call { search("sungold") }
                    .getOrNull()
                    ?.data
                    .orEmpty()

            val hit = hits.single()
            assertEquals("t_1", hit.topicId)
            assertEquals(listOf("Sungold"), hit.excerpt.filter { it.match }.map { it.text })
            assertTrue(
                session
                    .call {
                        search(
                            "sungold",
                            categoryId = "c_fruit",
                        )
                    }.getOrNull()
                    ?.data!!
                    .isEmpty(),
            )
        }

    @Test
    fun `BR-06 Unread lists followed topics first, then others, and follows unread changes`() =
        runBlocking {
            val basil = fake.addTopic("Basil all summer")
            fake.follow(FakePeople.Ada, basil.id)
            fake.unread(FakePeople.Ada, basil.id, 1)
            fake.unread(FakePeople.Ada, "t_1", 2)
            val session = signedIn()
            session.socket.connect()
            withTimeout(5.seconds) { session.socket.state.first { it == SocketState.Connected } }
            val feed = UnreadFeed(session, scope).also { it.start() }

            val first = feed.state.until { !it.others.loading }
            assertEquals(listOf(basil.id), first.following.map { it.id })
            assertEquals(listOf("t_1"), first.others.items.map { it.id })
            withTimeout(5.seconds) { while (!fake.joined("user:me")) delay(20) }

            fake.unread(FakePeople.Ada, "t_1", 0)
            val after = feed.state.until { it.others.items.isEmpty() && !it.others.refreshing }
            assertEquals(listOf(basil.id), after.following.map { it.id })
            assertFalse(after.empty)
        }

    @Test
    fun `BR-07 BR-11 the welcome card first, roles saved with Continue, then Getting started`() =
        runBlocking {
            fake.newMember(FakePeople.Ada)
            fake.signedInOnWeb(FakePeople.Ada)
            val welcome = Welcome(signedIn(), scope).also { it.refresh() }

            val first = welcome.state.until { !it.loading }
            assertEquals(MemberCard.Welcome, first.card)
            assertEquals("1. Be kind.\n2. No selling.", first.server?.rulesMd)
            welcome.tick("r_bees")
            welcome.continueWelcome()

            val next = welcome.state.until { it.saved && !it.saving }
            assertEquals(setOf("r_bees"), fake.rolesOf(FakePeople.Ada))
            assertEquals(MemberCard.GettingStarted, next.card)

            welcome.closeIntro(hide = true)
            val closed = welcome.state.until { it.card == null && !it.saving }
            assertEquals(setOf("r_bees"), closed.held)
            fake.signedInOnWeb(FakePeople.Ada)
            welcome.refresh()
            assertNull(welcome.state.until { !it.loading && it.me?.introDue == false }.card)
        }

    @Test
    fun `BR-07 Rules & Roles takes and leaves self-select roles`() =
        runBlocking {
            fake.welcomed(FakePeople.Ada)
            val welcome = Welcome(signedIn(), scope).also { it.refresh() }
            welcome.state.until { !it.loading }
            welcome.tick("r_gardener")
            welcome.save()
            welcome.state.until { it.saved }
            assertEquals(setOf("r_gardener"), fake.rolesOf(FakePeople.Ada))

            welcome.tick("r_gardener")
            welcome.tick("r_bees")
            welcome.save()
            val saved = welcome.state.until { it.saved }
            assertEquals(setOf("r_bees"), fake.rolesOf(FakePeople.Ada))
            assertEquals(setOf("r_bees"), saved.chosen)
        }

    @Test
    fun `BR-09 a user card, and Ignore then Stop ignoring`() =
        runBlocking {
            val session = signedIn()
            val mina = FakePeople.Mina.id!!
            val card = session.call { user(mina) }.getOrNull()
            assertEquals("Mina", card?.displayName?.substringBefore(' '))
            assertEquals(false, card?.ignoredByMe)

            assertEquals(true, session.call { ignore(mina, true) }.getOrNull()?.ignoredByMe)
            assertEquals(false, session.call { ignore(mina, false) }.getOrNull()?.ignoredByMe)
            assertNull(session.call { user(FakePeople.Ada.id!!) }.getOrNull()?.ignoredByMe)
        }
}
