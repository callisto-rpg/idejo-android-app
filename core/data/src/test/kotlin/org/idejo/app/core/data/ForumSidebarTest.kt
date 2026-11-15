// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.idejo.app.core.api.DiscoveryClient
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.model.Category
import org.idejo.app.core.model.CategoryGroup
import org.idejo.app.core.model.CategoryPermissions
import org.idejo.app.core.model.MentionState
import org.idejo.app.core.model.TokenGrant
import org.idejo.app.core.realtime.SocketState
import org.idejo.app.core.testing.FakeIdejo
import org.idejo.app.core.testing.FakePeople
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator
import kotlin.time.Duration.Companion.seconds

/** The left sidebar's data against the fake forum (SH-31 to SH-34, SH-37, IX-02 fallback). */
@OptIn(ExperimentalCoroutinesApi::class)
class ForumSidebarTest {
    @get:Rule val folder = TemporaryFolder()

    private val fake = FakeIdejo()
    private val http = IdejoHttp.forLocalTesting()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
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
        main.cancel()
        scope.cancel()
    }

    private fun signedIn(): ForumSession =
        runBlocking {
            val found = DiscoveryClient(http).discover(fake.address) as DiscoveryResult.Found
            val grant = TokenGrant(fake.tokenFor(Ada), tokenId = "tok_1", user = Ada)
            sessions.add(found, grant)
            requireNotNull(sessions.active)
        }

    private fun sidebar(session: ForumSession = signedIn()): ForumSidebar =
        ForumSidebar(session, main).also { it.start() }

    private suspend fun ForumSidebar.loaded(): SidebarState =
        withTimeout(5.seconds) { state.first { !it.loading } }

    private suspend fun eventually(check: () -> Boolean) {
        withTimeout(5.seconds) { while (!check()) delay(20) }
    }

    @Test
    fun `SH-31 categories in the home page's order, with the topics followed in each`() =
        runBlocking {
            fake.addCategory(category("c_fruit", "Fruit", CategoryGroup("g_orchard", "Orchard", 2)))
            fake.addCategory(category("c_herbs", "Herbs", CategoryGroup("g_kitchen", "Kitchen", 1)))
            val basil = fake.addTopic("Basil all summer", "c_herbs")
            fake.addTopic("Peppers indoors", "c_veg")
            fake.follow(Ada, "t_1")
            fake.follow(Ada, basil.id)

            val state = sidebar().loaded()

            assertEquals(
                listOf("Kitchen", "Orchard", null),
                state.sections.map { it.group?.name },
            )
            assertEquals(
                listOf("Herbs", "Fruit", "Vegetables"),
                state.sections.flatMap { s -> s.categories.map { it.category.title } },
            )
            val topics =
                state.sections.flatMap { s ->
                    s.categories.map { c -> c.topics.map { it.title } }
                }
            assertEquals(
                listOf(listOf("Basil all summer"), emptyList(), listOf("Tomato plans for spring")),
                topics,
            )
        }

    @Test
    fun `SH-32 a topic's dot and the Unread counter come from what is unread`() =
        runBlocking {
            fake.follow(Ada, "t_1")
            fake.unread(Ada, "t_1", 2, MentionState.Mention)

            val state = sidebar().loaded()

            val topic = state.topics.single()
            assertEquals(MentionState.Mention, topic.mentionState)
            assertEquals(2, topic.unreadCount)
            assertEquals(2, state.unread.count)
            assertEquals("mention", state.unread.level)
        }

    @Test
    fun `SH-32 a message in a followed topic glows and brings its dot, live`() =
        runBlocking {
            fake.follow(Ada, "t_1")
            val session = signedIn()
            session.socket.connect()
            withTimeout(5.seconds) { session.socket.state.first { it == SocketState.Connected } }
            val sidebar = sidebar(session)
            sidebar.loaded()
            eventually { fake.joined("category:c_veg") && fake.joined("user:me") }

            fake.post(FakePeople.Mina, "t_1", "Sungold every year")

            eventually { "t_1" in sidebar.state.value.glowing }
            eventually {
                sidebar.state.value.topics
                    .single()
                    .mentionState == MentionState.New
            }
        }

    @Test
    fun `SH-34 the x unfollows the topic and the + follows it again`() =
        runBlocking {
            fake.follow(Ada, "t_1")
            val sidebar = sidebar()
            val topic = sidebar.loaded().topics.single()

            sidebar.follow(topic, false).join()
            assertFalse(fake.follows(Ada, "t_1"))
            assertTrue(
                sidebar.state.value.topics
                    .isEmpty(),
            )

            sidebar.follow(topic, true).join()
            assertTrue(fake.follows(Ada, "t_1"))
            assertEquals(
                listOf("t_1"),
                sidebar.state.value.topics
                    .map { it.id },
            )
        }

    @Test
    fun `SH-37 unfollow and mute, and mark unread`() =
        runBlocking {
            val peppers = fake.addTopic("Peppers indoors", "c_veg")
            fake.follow(Ada, "t_1")
            fake.follow(Ada, peppers.id)
            val sidebar = sidebar()
            val (tomatoes, pepperRow) = sidebar.loaded().topics.sortedBy { it.id }

            sidebar.markUnread(tomatoes).join()
            assertEquals(
                MentionState.New,
                sidebar.state.value.topics
                    .first { it.id == "t_1" }
                    .mentionState,
            )

            sidebar.unfollowAndMute(pepperRow).join()
            assertTrue(fake.mutes(Ada, peppers.id))
            assertEquals(
                listOf("t_1"),
                sidebar.state.value.topics
                    .map { it.id },
            )
        }

    @Test
    fun `SI-05 a visitor sees the categories, with no followed topics and no Unread`() =
        runBlocking {
            val found = DiscoveryClient(http).discover(fake.address) as DiscoveryResult.Found
            sessions.add(found)
            val state = sidebar(requireNotNull(sessions.active)).loaded()

            assertEquals(
                listOf("Vegetables"),
                state.sections.flatMap { s ->
                    s.categories.map {
                        it.category.title
                    }
                },
            )
            assertTrue(state.topics.isEmpty())
            assertFalse(fake.requests.any { it.url.encodedPath == "/api/v1/unread" })
        }

    @Test
    fun `IX-02 on an older server, the fallback reads at most three pages of each category`() =
        runBlocking {
            fake.followedList = false
            repeat(320) { fake.addTopic("Topic $it", "c_veg") }
            sidebar().loaded()

            val pages =
                fake.requests.count {
                    it.url.encodedPath ==
                        "/api/v1/categories/c_veg/topics"
                }
            assertEquals(3, pages)
        }

    @Test
    fun `IX-02 the followed topics, in the order followed, however old`() =
        runBlocking {
            val old = fake.addTopic("Seed swap", "c_veg")
            repeat(320) { fake.addTopic("Topic $it", "c_veg") }
            fake.follow(Ada, old.id)
            fake.follow(Ada, "t_1")

            val state = sidebar().loaded()

            val topics = state.sections.flatMap { s -> s.categories.flatMap { it.topics } }
            assertEquals(listOf("Seed swap", "Tomato plans for spring"), topics.map { it.title })
            assertEquals(0, fake.requests.count { it.url.encodedPath.endsWith("/topics") })
        }

    private fun category(
        id: String,
        title: String,
        group: CategoryGroup,
    ) = Category(
        id = id,
        title = title,
        slug = title.lowercase(),
        group = group,
        permissions = CategoryPermissions(view = true),
    )

    private companion object {
        val Ada = FakePeople.Ada
    }
}
