// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.DiscoveryClient
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.model.MePatch
import org.idejo.app.core.model.TokenGrant
import org.idejo.app.core.testing.FakeIdejo
import org.idejo.app.core.testing.FakePeople
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator

/** The forum's one copy of `GET /me` and `PATCH /me` (ST-02, IX-15). */
class MeRepositoryTest {
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
    private val ada = FakePeople.Ada

    @After
    fun stop() {
        sessions.active?.socket?.disconnect()
        fake.close()
        scope.cancel()
    }

    private suspend fun signedIn(): ForumSession {
        val found = DiscoveryClient(http).discover(fake.address) as DiscoveryResult.Found
        sessions.add(found, TokenGrant(fake.tokenFor(ada), tokenId = "tok_1", user = ada))
        return requireNotNull(sessions.active)
    }

    @Test
    fun `ST-02 PATCH me sends only the changed field and publishes the server's answer`() =
        runBlocking<Unit> {
            val me = signedIn().me
            me.refresh()
            assertEquals(true, me.state.value?.graphicalComposer)

            val result = me.update(MePatch(graphicalComposer = false))

            assertTrue(result is ApiResult.Success)
            val patch = fake.requests.last { it.method == "PATCH" }
            assertEquals("""{"graphical_composer":false}""", patch.body?.utf8())
            assertEquals(false, me.state.value?.graphicalComposer)
            assertEquals(true, me.state.value?.autoPunctuation)
            assertEquals(false, fake.choicesOf(ada).graphicalComposer)
        }

    @Test
    fun `ST-02 a refused change leaves the member as the server last said`() =
        runBlocking<Unit> {
            val me = signedIn().me
            me.refresh()
            fake.failNext(422, "invalid_request", "That choice cannot be saved.")

            assertTrue(me.update(MePatch(autoPunctuation = false)) is ApiResult.Failure)
            assertEquals(true, me.state.value?.autoPunctuation)
        }

    @Test
    fun `IX-15 a server without the choices gives none, not a guess`() =
        runBlocking<Unit> {
            fake.memberChoices = false
            val me = signedIn().me
            me.refresh()
            assertNull(me.state.value?.graphicalComposer)
            assertNull(me.state.value?.controlsOnMobile)
        }
}
