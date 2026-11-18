// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import org.idejo.app.core.model.TokenGrant
import org.idejo.app.core.realtime.SocketState
import org.idejo.app.core.testing.FakeIdejo
import org.idejo.app.core.testing.FakePeople
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator
import kotlin.time.Duration.Companion.seconds

/** The live bubbles' data against the fake forum's user:me channel (NT-01). */
class LiveBubblesTest {
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

    private fun listening(): LiveBubbles =
        runBlocking {
            val found = DiscoveryClient(http).discover(fake.address) as DiscoveryResult.Found
            val grant =
                TokenGrant(fake.tokenFor(FakePeople.Ada), tokenId = "tok_1", user = FakePeople.Ada)
            sessions.add(found, grant)
            val session = requireNotNull(sessions.active)
            val bubbles = LiveBubbles(session, scope).also { it.start() }
            session.socket.connect()
            withTimeout(5.seconds) { session.socket.state.first { it == SocketState.Connected } }
            eventually { fake.joined("user:me") }
            bubbles
        }

    private suspend fun eventually(check: () -> Boolean) {
        withTimeout(5.seconds) { while (!check()) delay(20) }
    }

    @Test
    fun `NT-01 each kind of notification makes its bubble, newest first`() =
        runBlocking {
            val bubbles = listening()
            fake.notify(FakePeople.Ada, "mention", "t_1", messageId = "m_1")
            eventually { bubbles.state.value.size == 1 }
            fake.notify(FakePeople.Ada, "reaction", "t_1", emojiUrl = "https://e/heart.png")

            eventually { bubbles.state.value.size == 2 }
            val (reaction, mention) = bubbles.state.value
            assertEquals(NotificationKind.Reaction, reaction.kind)
            assertEquals("https://e/heart.png", reaction.emojiUrl)
            assertEquals(NotificationKind.Mention, mention.kind)
            assertEquals("Mina", mention.authorName)
            assertEquals("Tomatoes", mention.topicTitle)
            assertEquals("Sungold every year", mention.excerpt)
            assertEquals("m_1", mention.messageId)
        }

    @Test
    fun `NT-01 three at most, and none about the topic on screen`() =
        runBlocking {
            val bubbles = listening()
            bubbles.onScreen = "t_open"
            fake.notify(FakePeople.Ada, "follow", "t_open")
            listOf("reply", "role_mention", "follow", "mention").forEach {
                fake.notify(FakePeople.Ada, it, "t_2")
            }

            eventually {
                bubbles.state.value
                    .firstOrNull()
                    ?.kind == NotificationKind.Mention
            }
            val kinds = bubbles.state.value.map { it.kind }
            assertEquals(
                listOf(
                    NotificationKind.Mention,
                    NotificationKind.Followed,
                    NotificationKind.RoleMention,
                ),
                kinds,
            )
            assertTrue(bubbles.state.value.none { it.topicId == "t_open" })
        }

    @Test
    fun `NT-01 a warning shows as the card, not a bubble, and a bubble can be dismissed`() =
        runBlocking {
            val bubbles = listening()
            fake.warn(FakePeople.Ada, "Be kind", messageId = "m_1")
            fake.notify(FakePeople.Ada, "reply", "t_1")

            eventually { bubbles.state.value.size == 1 }
            assertEquals(
                NotificationKind.Reply,
                bubbles.state.value
                    .single()
                    .kind,
            )
            bubbles.dismiss(
                bubbles.state.value
                    .single()
                    .key,
            )
            assertTrue(bubbles.state.value.isEmpty())
        }
}
