// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.DiscoveryClient
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.model.TokenGrant
import org.idejo.app.core.testing.FakeIdejo
import org.idejo.app.core.testing.FakePeople
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import javax.crypto.KeyGenerator

/** The forums' last answers on the device (OF-01), shown while offline (OF-02). */
@RunWith(RobolectricTestRunner::class)
class PageCacheTest {
    @get:Rule val folder = TemporaryFolder()

    private val database =
        Room
            .inMemoryDatabaseBuilder(
                org.robolectric.RuntimeEnvironment.getApplication(),
                CacheDatabase::class.java,
            ).allowMainThreadQueries()
            .build()
    private var now = 1_000L
    private var limit = Long.MAX_VALUE
    private val cache = PageCache(database.pages(), { limit }) { now++ }
    private val garden = "https://garden.example.org/"

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
            pages = cache,
        ) { ForumAddress.forLocalTesting(it.toHttpUrl()) }
    }

    @After
    fun stop() {
        sessions.active?.socket?.disconnect()
        fake.close()
        scope.cancel()
        database.close()
    }

    @Test
    fun `OF-01 the last 20 topics are kept, each with its window`() =
        runBlocking {
            repeat(22) { n ->
                cache.put(garden, PageCache.TOPIC + "t_$n", "{}")
                cache.put(garden, PageCache.WINDOW + "t_$n", "{}")
            }
            assertNull(cache.get(garden, PageCache.TOPIC + "t_0"))
            assertNull(cache.get(garden, PageCache.WINDOW + "t_1"))
            assertNotNull(cache.get(garden, PageCache.TOPIC + "t_2"))
            assertNotNull(cache.get(garden, PageCache.WINDOW + "t_21"))
        }

    @Test
    fun `OF-01 within the size limit, the least recently shown first out`() =
        runBlocking {
            cache.put(garden, "categories", "a".repeat(100))
            cache.put(garden, "unread", "b".repeat(100))
            cache.get(garden, "categories")
            limit = 450
            cache.put(garden, "server", "c".repeat(100))

            assertNull(cache.get(garden, "unread"))
            assertNotNull(cache.get(garden, "categories"))
            assertNotNull(cache.get(garden, "server"))
            assertTrue(cache.bytes() <= limit)

            cache.forget(garden)
            assertEquals(0L, cache.bytes())
        }

    @Test
    fun `OF-02 OF-05 an unreachable forum answers from the saved copy, and says from when`() =
        runBlocking {
            val found = DiscoveryClient(http).discover(fake.address) as DiscoveryResult.Found
            sessions.add(found, TokenGrant(fake.tokenFor(FakePeople.Ada), user = FakePeople.Ada))
            val session = requireNotNull(sessions.active)
            val read = session.cached("categories") { categories() }
            val names = (read as ApiResult.Success).value.map { it.title }
            assertNull(session.savedCopy.value)

            assertTrue(!session.offline.value)
            fake.close()
            val offline = session.cached("categories") { categories() }
            // OF-05: the forum is offline for what is not queued.
            assertTrue(session.offline.value)
            assertEquals(names, (offline as ApiResult.Success).value.map { it.title })
            assertNotNull(session.savedCopy.value)

            // Nothing saved: the failure, as before.
            assertTrue(session.cached("active:false") { activeTopics(false) } is ApiResult.Failure)
        }

    @Test
    fun `OF-05 the network lost is offline at once, back asks the forum again`() =
        runBlocking {
            val found = DiscoveryClient(http).discover(fake.address) as DiscoveryResult.Found
            sessions.add(found, TokenGrant(fake.tokenFor(FakePeople.Ada), user = FakePeople.Ada))
            val session = requireNotNull(sessions.active)
            val network = kotlinx.coroutines.flow.MutableStateFlow(true)
            NetworkWatch(sessions, network).start(scope)
            val asked = fake.requests.size

            network.value = false
            kotlinx.coroutines.withTimeout(5_000) { session.offline.first { it } }

            network.value = true
            kotlinx.coroutines.withTimeout(5_000) { session.offline.first { !it } }
            assertTrue(fake.requests.drop(asked).any { it.url.encodedPath == "/api/v1/server" })
        }
}
