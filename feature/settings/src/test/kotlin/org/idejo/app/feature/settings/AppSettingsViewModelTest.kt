// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.idejo.app.core.data.AppChoices
import org.idejo.app.core.data.DistributorChoice
import org.idejo.app.core.data.ForumStore
import org.idejo.app.core.data.MessageText
import org.idejo.app.core.data.PushDistributor
import org.idejo.app.core.data.PushRegistrar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

/** App settings on this device (ST-20 to ST-22). */
class AppSettingsViewModelTest {
    @get:Rule val forum = SignedInForum()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store =
        ForumStore.create(Files.createTempDirectory("app").toFile().resolve("app.json"), scope)
    private val choices = AppChoices(store)

    /** Two distributors installed, ntfy in use; registrations as the connector would take. */
    private class Distributors : PushDistributor {
        var current: String? = "io.heckel.ntfy"
        val registered = CopyOnWriteArrayList<String>()

        override fun choice() = DistributorChoice.Ready

        override fun register(
            instance: String,
            name: String,
            vapid: String?,
        ) {
            registered += "$current:$instance"
        }

        override fun unregister(instance: String) = Unit

        override fun installed() = listOf("io.heckel.ntfy", "org.idejo.app")

        override fun current() = current

        override fun use(distributor: String) {
            current = distributor
        }
    }

    private class Cache : AppCache {
        var bytes = 12_000_000L

        override suspend fun size() = bytes

        override suspend fun clear() {
            bytes = 40_000
        }
    }

    private val distributors = Distributors()
    private val cache = Cache()

    private fun viewModel(): AppSettingsViewModel {
        val registrar = PushRegistrar(forum.sessions, distributors, "Pixel 8", scope)
        return AppSettingsViewModel(choices, registrar, cache)
    }

    @After
    fun stop() = scope.cancel()

    @Test
    fun `ST-20 the push services installed, and the one in use`() {
        val state = viewModel().state.until { it.distributors.isNotEmpty() }
        assertEquals(listOf("io.heckel.ntfy", "org.idejo.app"), state.distributors)
        assertEquals("io.heckel.ntfy", state.distributor)
    }

    @Test
    fun `ST-20 another push service is used at once for the forum open`() {
        forum.signIn()
        val vm = viewModel()
        vm.onEvent(AppSettingsEvent.UseDistributor("org.idejo.app"))
        assertEquals(
            "org.idejo.app",
            vm.state.until { it.distributor == "org.idejo.app" }.distributor,
        )
        runBlocking {
            withTimeout(5_000) {
                while (distributors.registered.none { it.startsWith("org.idejo.app:") }) {
                    delay(20)
                }
            }
        }
    }

    @Test
    fun `ST-21 the cache's size, its limit, and Clear cache`() {
        val vm = viewModel()
        assertEquals(12_000_000L, vm.state.until { it.cacheBytes != null }.cacheBytes)
        assertEquals(200, vm.state.value.cacheLimitMb)

        vm.onEvent(AppSettingsEvent.SetCacheLimit(500))
        assertEquals(500, vm.state.until { it.cacheLimitMb == 500 }.cacheLimitMb)
        vm.onEvent(AppSettingsEvent.ClearCache)
        assertEquals(
            40_000L,
            vm.state.until { it.cacheBytes == 40_000L && !it.clearing }.cacheBytes,
        )
    }

    @Test
    fun `ST-22 text size, haptics and reduced motion are kept for the whole app`() {
        val vm = viewModel()
        vm.onEvent(AppSettingsEvent.SetMessageText(MessageText.Large))
        vm.onEvent(AppSettingsEvent.SetHaptics(false))
        vm.onEvent(AppSettingsEvent.SetReduceMotion(true))

        val state =
            vm.state.until {
                it.reduceMotion && !it.haptics &&
                    it.messageText == MessageText.Large
            }
        assertFalse(state.haptics)
        val kept = runBlocking { choices.display.first() }
        assertEquals(MessageText.Large, kept.messageText)
        assertFalse(kept.haptics)
        assertEquals(true, kept.reduceMotion)
    }
}
