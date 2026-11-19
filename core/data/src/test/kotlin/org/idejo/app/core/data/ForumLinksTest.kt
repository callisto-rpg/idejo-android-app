// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.idejo.app.core.api.DiscoveryClient
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.model.TokenGrant
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

/** The pages a notification opens (NT-04). */
class ForumLinksTest {
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

    private val session by lazy {
        runBlocking {
            val found = DiscoveryClient(http).discover(fake.address) as DiscoveryResult.Found
            val grant =
                TokenGrant(fake.tokenFor(FakePeople.Ada), tokenId = "tok_1", user = FakePeople.Ada)
            sessions.add(found, grant)
            requireNotNull(sessions.active)
        }
    }

    private fun target(path: String) =
        runBlocking {
            linkTarget(
                session,
                session.origin.trimEnd('/') + path,
            )
        }

    @Test
    fun `NT-04 a mention opens where the member left off in its topic`() {
        val message = fake.post(FakePeople.Mina, "t_1", "@ada look")
        assertEquals(LinkTarget.Topic("t_1"), target("/m/${message.id}?unread"))
    }

    @Test
    fun `NT-04 a reaction opens at the message`() {
        val message = fake.post(FakePeople.Mina, "t_1", "Sungold")
        assertEquals(LinkTarget.Topic("t_1", message.id), target("/m/${message.id}"))
    }

    @Test
    fun `NT-04 a private message opens its conversation`() {
        val id = fake.conversation(FakePeople.Ada, FakePeople.Mina)
        val message = fake.post(FakePeople.Mina, id, "Hello")
        assertEquals(LinkTarget.Topic(id, private = true), target("/m/${message.id}?unread"))
        assertEquals(LinkTarget.Topic(id, private = true), target("/p/$id"))
        assertEquals(LinkTarget.Conversations, target("/p"))
    }

    @Test
    fun `NT-04 several mentions open Unread, and a topic its page`() {
        assertEquals(LinkTarget.Unread, target("/unread"))
        assertEquals(LinkTarget.Topic("t_1"), target("/t/t_1/tomatoes"))
    }

    @Test
    fun `NT-04 another forum's address, or one the app has no page for, opens the home page`() {
        val elsewhere = runBlocking { linkTarget(session, "https://evil.example.com/t/t_1") }
        assertEquals(LinkTarget.Home, elsewhere)
        assertEquals(LinkTarget.Home, target("/admin"))
        assertEquals(LinkTarget.Home, target("/m/m_missing?unread"))
    }

    @Test
    fun `NT-04 a pending link opens once, in its own forum`() {
        val links = PendingLinks()
        links.offer(PendingLink("https://a.example/", "https://a.example/unread"))
        assertNull(links.take("https://b.example/"))
        assertEquals("https://a.example/unread", links.take("https://a.example/")?.url)
        assertNull(links.take("https://a.example/"))
    }

    @Test
    fun `IB-08 a topic tapped in the inbox's Topics waits for its forum, Back then to the inbox`() {
        val links = PendingLinks()
        links.offer(PendingLink("https://a.example/", "https://a.example/t/t_1", fromInbox = true))
        assertNull(links.take("https://b.example/"))
        val link = requireNotNull(links.take("https://a.example/"))
        assertTrue(link.fromInbox)
        // A notification tapped leads back through the forum, as before.
        links.offer(PendingLink("https://a.example/", "https://a.example/unread"))
        assertFalse(requireNotNull(links.take("https://a.example/")).fromInbox)
    }
}
