// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.idejo.app.core.api.DiscoveryClient
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.data.AesGcmBox
import org.idejo.app.core.data.ForumSession
import org.idejo.app.core.data.ForumStore
import org.idejo.app.core.data.SessionManager
import org.idejo.app.core.data.TokenVault
import org.idejo.app.core.model.TokenGrant
import org.idejo.app.core.testing.FakeIdejo
import org.idejo.app.core.testing.FakePeople
import org.junit.rules.ExternalResource
import java.nio.file.Files
import javax.crypto.KeyGenerator
import kotlin.time.Duration.Companion.seconds

/**
 * Ada signed in to [fake], for a settings section's ViewModel tests: `@get:Rule val forum =
 * SignedInForum()`, then build the ViewModel with [sessions] once [signIn] returns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignedInForum : ExternalResource() {
    val fake = FakeIdejo()
    val ada = FakePeople.Ada
    private val http = IdejoHttp.forLocalTesting()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val folder = Files.createTempDirectory("settings").toFile()
    val sessions: SessionManager by lazy {
        SessionManager(
            ForumStore.create(folder.resolve("app.json"), scope),
            TokenVault(AesGcmBox { key }),
            http,
            scope,
            Dispatchers.IO,
        ) { ForumAddress.forLocalTesting(it.toHttpUrl()) }
    }

    override fun before() = Dispatchers.setMain(UnconfinedTestDispatcher())

    override fun after() {
        sessions.active?.socket?.disconnect()
        fake.close()
        scope.cancel()
        folder.deleteRecursively()
        Dispatchers.resetMain()
    }

    fun signIn(): ForumSession =
        runBlocking {
            val found = DiscoveryClient(http).discover(fake.address) as DiscoveryResult.Found
            sessions.add(found, TokenGrant(fake.tokenFor(ada), tokenId = "tok_1", user = ada))
            requireNotNull(sessions.active)
        }

    /** The server stops answering: the next calls fail as offline. */
    fun goOffline() = fake.close()
}

/** The first value of [this] that is [done], within five seconds. */
fun <T> StateFlow<T>.until(done: (T) -> Boolean): T =
    runBlocking { withTimeout(5.seconds) { first(done) } }

/** Waits until [check] holds, within five seconds. */
fun eventually(check: () -> Boolean) =
    runBlocking { withTimeout(5.seconds) { while (!check()) delay(20) } }
