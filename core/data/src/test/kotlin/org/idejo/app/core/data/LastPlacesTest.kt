// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.model.Discovery
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.crypto.KeyGenerator
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The last place opened, restored on the next launch (MD-05). */
class LastPlacesTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val store by lazy { ForumStore.create(folder.root.resolve("app.json"), scope) }
    private val garden = "https://garden.example.org/"
    private val bees = "https://bees.example.org/"
    private val inbox = "https://my.example.org/"

    @After
    fun stop() = scope.cancel()

    /** A launch: a manager and its places, from what the store keeps. */
    private fun launch(): LastPlaces {
        val sessions =
            SessionManager(
                store,
                TokenVault(AesGcmBox { key }),
                IdejoHttp.forLocalTesting(),
                scope,
                Dispatchers.IO,
            ) { ForumAddress.forLocalTesting(it.toHttpUrl()) }
        return LastPlaces(sessions, store, scope, settle = 10.milliseconds)
    }

    private fun record(origin: String) = ForumRecord(origin, Discovery(origin, origin))

    private suspend fun keptAt(origin: String): String? = store.current().record(origin)?.lastPlace

    private suspend fun waitFor(check: suspend () -> Boolean) =
        withTimeout(5.seconds) { while (!check()) delay(10) }

    @Test
    fun `MD-05 the forum open at the last launch opens at its last page, once`() =
        runBlocking {
            store.update { it.copy(forums = listOf(record(garden), record(bees)), active = garden) }
            val first = launch()
            first.keep(garden, "a topic")
            first.keep(bees, "a category")
            waitFor { keptAt(garden) == "a topic" && keptAt(bees) == "a category" }

            val next = launch()
            assertNull(next.atLaunch(bees))
            assertEquals("a topic", next.atLaunch(garden))
            // Entering it again later in the same launch opens its home.
            assertNull(next.atLaunch(garden))
        }

    @Test
    fun `MD-05 the inbox open at the last launch shows its tab, its chat`() =
        runBlocking {
            store.update {
                it.copy(
                    mode = AppMode.IdejoId,
                    inbox = record(inbox),
                    forums = listOf(record(garden)),
                )
            }
            val first = launch()
            first.keepInboxTab("Topics")
            first.keep(inbox, "a chat")
            waitFor { keptAt(inbox) == "a chat" }

            val next = launch()
            assertEquals("Topics", next.inboxTabAtLaunch())
            assertNull(next.inboxTabAtLaunch())
            assertEquals("a chat", next.atLaunch(inbox))
            assertNull(next.atLaunch(garden))
        }

    @Test
    fun `MD-05 a forum open at the last launch keeps the inbox's tab for later`() =
        runBlocking {
            store.update {
                it.copy(
                    mode = AppMode.IdejoId,
                    inbox = record(inbox),
                    forums = listOf(record(garden)),
                    active = garden,
                    inboxTab = "Chats",
                )
            }
            assertNull(launch().inboxTabAtLaunch())
        }

    @Test
    fun `SH-12 a forum changed keeps its place among the others`() {
        val state = AppState(forums = listOf(record(garden), record(bees)))
        val changed = state.with(record(garden).copy(lastPlace = "a topic"))
        assertEquals(listOf(garden, bees), changed.forums.map { it.origin })
        assertEquals(
            listOf(garden, bees, inbox),
            changed.with(record(inbox)).forums.map { it.origin },
        )
    }
}
