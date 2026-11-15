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

/** Where a topic continues from, against the fake forum (idejo TP-09). */
class ContinuedFromTest {
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

    @Test
    fun `TP-09 a topic started from a message names it and its topic`() =
        runBlocking {
            val session = signedIn()
            val started = fake.addTopic("Saving seeds")
            fake.continueFrom(started.id, "m_1")
            val found = session.call { topic(started.id) }.getOrNull()!!
            val from = session.continuedFrom(found)!!
            assertEquals("m_1", from.message.id)
            assertEquals("Leo Moreau", from.message.author.displayName)
            assertEquals("Tomato plans for spring", from.topicTitle)
        }

    @Test
    fun `TP-09 none when the topic was not continued, or its source is gone`() =
        runBlocking {
            val session = signedIn()
            val plain = session.call { topic("t_1") }.getOrNull()!!
            assertNull(session.continuedFrom(plain))
            val started = fake.addTopic("Saving seeds")
            fake.continueFrom(started.id, "m_none")
            assertNull(session.continuedFrom(session.call { topic(started.id) }.getOrNull()!!))
            fake.continueFrom(started.id, "m_1")
            fake.deleteByModerator("m_1")
            assertNull(session.continuedFrom(session.call { topic(started.id) }.getOrNull()!!))
        }

    @Test
    fun `SH-04 IX-13 a topic gives the member's last message there`() =
        runBlocking {
            val session = signedIn()
            assertNull(session.call { topic("t_1") }.getOrNull()!!.myLastMessageId)
            val mine = fake.post(FakePeople.Ada, "t_1", "Count me in")
            fake.post(FakePeople.Mina, "t_1", "Me too")
            assertEquals(mine.id, session.call { topic("t_1") }.getOrNull()!!.myLastMessageId)
        }
}
