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
import org.idejo.app.core.model.ApiError
import org.idejo.app.core.model.Mention
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

/** The cards inline links open, against the fake forum (TV-22). */
class InlineCardsTest {
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

    private fun mention(
        type: String,
        id: String? = null,
        slug: String? = null,
    ) = Mention(type, text = "x", id = id, slug = slug)

    private suspend fun InlineCards.card(mention: Mention): InlineCard =
        (load(mention) as ApiResult.Success).value

    @Test
    fun `TV-22 a topic card has its category, opening words and Follow`() =
        runBlocking {
            val cards = InlineCards(signedIn())
            val card = cards.card(mention("topic", id = "t_1")) as InlineCard.Topic
            assertEquals("Tomato plans for spring", card.topic.title)
            assertEquals("Vegetables", card.category?.title)
            assertEquals("Planning this year's beds.", card.opening)
            assertFalse(card.topic.followed)
            val followed = cards.follow("t_1", true) as ApiResult.Success
            assertTrue(followed.value.followed)
        }

    @Test
    fun `TV-22 a category card has its latest topics`() =
        runBlocking {
            val card =
                InlineCards(signedIn()).card(mention("category", id = "c_veg"))
                    as InlineCard.Category
            assertEquals("Vegetables", card.category.title)
            assertEquals(listOf("t_1"), card.topics.map { it.id })
        }

    @Test
    fun `TV-22 a tag card has where it belongs and its topics`() =
        runBlocking {
            val card =
                InlineCards(signedIn()).card(mention("tag", slug = "seeds")) as InlineCard.Tag
            assertEquals("Seeds", card.tag.name)
            assertEquals(emptyList<Any>(), card.categories)
            assertEquals(listOf("t_1"), card.topics.map { it.id })
        }

    @Test
    fun `TV-22 a role card has its members`() =
        runBlocking {
            val session = signedIn()
            session.call { role("r_gardener", taken = true) }
            val card =
                InlineCards(session).card(mention("role", slug = "gardener")) as InlineCard.Role
            assertEquals("Gardener", card.role.name)
            assertEquals(1, card.role.membersCount)
            assertEquals(listOf("u_ada"), card.role.members.map { it.id })
        }

    @Test
    fun `TV-22 what the member may not view has no card`() =
        runBlocking {
            val cards = InlineCards(signedIn())
            listOf(
                mention("category", id = "c_hidden"),
                mention("topic", id = "t_none"),
                mention("tag", slug = "none"),
                mention("role", slug = "none"),
            ).forEach { link ->
                val problem = (cards.load(link) as ApiResult.Failure).problem as ApiProblem.Server
                assertEquals(ApiError.NOT_FOUND, problem.error.code)
            }
            assertNull(cards.load(mention("user", id = "u_mina")))
        }

    @Test
    fun `TV-22 a forum card comes from the link alone`() =
        runBlocking {
            val forum =
                Mention("forum", text = "&bees", id = "bees.example.org", name = "Bees")
            val card = InlineCards(signedIn()).card(forum) as InlineCard.Forum
            assertEquals("Bees", card.name)
            assertEquals("bees.example.org", card.host)
        }
}
