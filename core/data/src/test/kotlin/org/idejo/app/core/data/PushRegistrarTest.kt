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
import org.idejo.app.core.api.DiscoveryClient
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.IdejoHttp
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
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.KeyGenerator
import kotlin.time.Duration.Companion.seconds

/** This device's pushes registered with the forum (NT-02, NT-06, NT-09). */
class PushRegistrarTest {
    @get:Rule val folder = TemporaryFolder()

    private val fake = FakeIdejo()
    private val http = IdejoHttp.forLocalTesting()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val store by lazy { ForumStore.create(folder.root.resolve("app.json"), scope) }
    private val sessions by lazy {
        SessionManager(
            store,
            TokenVault(AesGcmBox { key }),
            http,
            scope,
            Dispatchers.IO,
        ) { ForumAddress.forLocalTesting(it.toHttpUrl()) }
    }

    /** The connector as the tests play it: what it was asked, and what it would answer. */
    private class FakeDistributor : PushDistributor {
        @Volatile var answer = DistributorChoice.Ready
        val registered = CopyOnWriteArrayList<Triple<String, String, String?>>()
        val unregistered = CopyOnWriteArrayList<String>()

        override fun choice() = answer

        override fun register(
            instance: String,
            name: String,
            vapid: String?,
        ) {
            registered += Triple(instance, name, vapid)
        }

        override fun unregister(instance: String) {
            unregistered += instance
        }

        @Volatile var removed = false

        override fun removeAll() {
            removed = true
        }
    }

    private val distributor = FakeDistributor()
    private val registrar by lazy {
        PushRegistrar(sessions, distributor, "Pixel 8", scope).also { it.start() }
    }

    @After
    fun stop() {
        fake.close()
        scope.cancel()
    }

    private fun signedIn(): ForumSession =
        runBlocking {
            registrar
            val found = DiscoveryClient(http).discover(fake.address) as DiscoveryResult.Found
            val grant =
                TokenGrant(fake.tokenFor(FakePeople.Ada), tokenId = "tok_1", user = FakePeople.Ada)
            sessions.add(found, grant)
            requireNotNull(sessions.active)
        }

    private suspend fun eventually(check: () -> Boolean) {
        withTimeout(5.seconds) { while (!check()) delay(20) }
    }

    private val pushes get() = fake.pushSubscriptions.values

    @Test
    fun `NT-02 signed in, the forum is registered with the distributor, its push key with it`() =
        runBlocking {
            val session = signedIn()
            eventually { distributor.registered.isNotEmpty() }
            val (instance, name, vapid) = distributor.registered.single()
            assertEquals(session.origin, instance)
            assertEquals("Garden club", name)
            assertEquals("B" + "A".repeat(86), vapid)
            assertEquals(PushStatus.Registering, registrar.status.value)
        }

    @Test
    fun `NT-02 the endpoint and keys go to the forum, with the device's name, once`() =
        runBlocking {
            val session = signedIn()
            eventually { distributor.registered.isNotEmpty() }
            registrar.onEndpoint(session.origin, "https://ntfy.example.net/upA", "BPUB", "AUTH")

            val sent = pushes.single()
            assertEquals("https://ntfy.example.net/upA", sent.endpoint)
            assertEquals("BPUB", sent.p256dh)
            assertEquals("AUTH", sent.auth)
            assertEquals("Pixel 8", sent.deviceName)
            assertEquals(PushStatus.Registered, registrar.status.value)
            assertEquals(
                "https://ntfy.example.net/upA",
                session.record.value.push
                    ?.endpoint,
            )

            // The same endpoint again (each launch): nothing new is sent.
            registrar.onEndpoint(session.origin, "https://ntfy.example.net/upA", "BPUB", "AUTH")
            assertEquals(
                1,
                fake.requests.count {
                    it.method == "POST" &&
                        it.url.encodedPath.endsWith("unifiedpush")
                },
            )
        }

    @Test
    fun `NT-02 a new endpoint replaces the old registration`() =
        runBlocking {
            val session = signedIn()
            registrar.onEndpoint(session.origin, "https://ntfy.example.net/upA", "BPUB", "AUTH")
            val first = requireNotNull(session.record.value.push).registrationId

            registrar.onEndpoint(session.origin, "https://ntfy.example.net/upB", "BPUB2", "AUTH2")

            assertEquals(
                listOf("https://ntfy.example.net/upB"),
                pushes.map { it.endpoint }.takeLast(1),
            )
            val deleted =
                fake.requests
                    .last { it.method == "DELETE" }
                    .url.encodedPath
            assertTrue(deleted.endsWith("/me/push/unifiedpush/$first"))
            assertEquals(
                "https://ntfy.example.net/upB",
                session.record.value.push
                    ?.endpoint,
            )
        }

    @Test
    fun `NT-02 signing out unregisters the forum from the distributor`() =
        runBlocking {
            val session = signedIn()
            registrar.onEndpoint(session.origin, "https://ntfy.example.net/upA", "BPUB", "AUTH")

            sessions.signOut(session.origin)

            eventually { session.origin in distributor.unregistered }
            eventually {
                sessions.active
                    ?.record
                    ?.value
                    ?.push == null
            }
            assertEquals(PushStatus.Off, registrar.status.value)
        }

    @Test
    fun `NT-02 an endpoint for a forum signed out of is dropped, not sent`() =
        runBlocking {
            registrar
            registrar.onEndpoint(
                "https://elsewhere.example.org/",
                "https://ntfy.example.net/x",
                "K",
                "A",
            )
            assertEquals(
                listOf("https://elsewhere.example.org/"),
                distributor.unregistered.toList(),
            )
            assertTrue(pushes.isEmpty())
        }

    @Test
    fun `NT-06 without a distributor, settings say so and nothing is registered`() =
        runBlocking {
            distributor.answer = DistributorChoice.NoneAvailable
            signedIn()
            eventually { registrar.status.value == PushStatus.NoDistributor }
            assertTrue(distributor.registered.isEmpty())
        }

    @Test
    fun `ST-20 with several distributors and none chosen, the member picks one`() =
        runBlocking {
            distributor.answer = DistributorChoice.ToSelect
            signedIn()
            eventually { registrar.status.value == PushStatus.ChooseDistributor }

            distributor.answer = DistributorChoice.Ready
            registrar.retry()
            eventually { distributor.registered.isNotEmpty() }
        }

    @Test
    fun `NT-02 a forum whose push is off is not registered`() =
        runBlocking {
            fake.pushEnabled = false
            signedIn()
            delay(300)
            assertTrue(distributor.registered.isEmpty())
            assertEquals(PushStatus.Off, registrar.status.value)
        }

    @Test
    fun `NT-02 a refused endpoint keeps nothing and says why`() =
        runBlocking {
            val session = signedIn()
            registrar.onEndpoint(session.origin, "http://ntfy.example.net/plain", "K", "A")
            assertTrue(registrar.status.value is PushStatus.Failed)
            assertNull(session.record.value.push)
        }

    @Test
    fun `NT-02 signed in again, the same endpoint is sent again`() =
        runBlocking {
            val session = signedIn()
            registrar.onEndpoint(session.origin, "https://ntfy.example.net/upA", "BPUB", "AUTH")
            sessions.signOut(session.origin)
            eventually { session.origin in distributor.unregistered }

            val grant =
                TokenGrant(fake.tokenFor(FakePeople.Ada), tokenId = "tok_2", user = FakePeople.Ada)
            sessions.signedIn(session.origin, grant)
            val again = requireNotNull(sessions.active)
            assertNull(again.record.value.push)
            registrar.onEndpoint(again.origin, "https://ntfy.example.net/upA", "BPUB", "AUTH")

            val posts =
                fake.requests.count {
                    it.method == "POST" &&
                        it.url.encodedPath.endsWith("unifiedpush")
                }
            assertEquals(2, posts)
        }

    @Test
    fun `NT-06 push turned off ends every registration, and nothing registers again`() =
        runBlocking {
            val session = signedIn()
            eventually { distributor.registered.isNotEmpty() }
            registrar.onEndpoint(
                session.origin,
                "https://fcm.googleapis.com/fcm/send/x",
                "BPUB",
                "AUTH",
            )
            val id = requireNotNull(session.record.value.push).registrationId

            AppChoices(store).pushOff(true)
            registrar.disable()

            assertTrue(distributor.removed)
            assertNull(session.record.value.push)
            assertEquals(PushStatus.Off, registrar.status.value)
            assertTrue(
                fake.requests.any {
                    it.method == "DELETE" && it.url.encodedPath.endsWith("/me/push/unifiedpush/$id")
                },
            )

            // Off, the forum is not registered again, and a late endpoint is given back.
            distributor.registered.clear()
            registrar.retry()
            delay(300)
            assertTrue(distributor.registered.isEmpty())
            registrar.onEndpoint(
                session.origin,
                "https://fcm.googleapis.com/fcm/send/y",
                "B2",
                "A2",
            )
            assertEquals(listOf(session.origin), distributor.unregistered.toList())
            assertNull(session.record.value.push)
        }

    @Test
    fun `NT-02 a new endpoint while the app is closed still reaches the forum`() =
        runBlocking {
            val session = signedIn()
            registrar.onEndpoint(
                session.origin,
                "https://fcm.googleapis.com/fcm/send/a",
                "BPUB",
                "AUTH",
            )
            val first = requireNotNull(session.record.value.push).registrationId

            // Started by the distributor: the same records, no session restored yet.
            val cold =
                SessionManager(store, TokenVault(AesGcmBox { key }), http, scope, Dispatchers.IO) {
                    ForumAddress.forLocalTesting(it.toHttpUrl())
                }
            val woken = PushRegistrar(cold, distributor, "Pixel 8", scope)
            assertNull(cold.active)
            woken.onEndpoint(
                session.origin,
                "https://fcm.googleapis.com/fcm/send/b",
                "BPUB2",
                "AUTH2",
            )

            assertTrue(distributor.unregistered.isEmpty())
            assertEquals("https://fcm.googleapis.com/fcm/send/b", pushes.last().endpoint)
            assertTrue(
                fake.requests.any {
                    it.method == "DELETE" &&
                        it.url.encodedPath.endsWith("/me/push/unifiedpush/$first")
                },
            )
            assertEquals(
                "https://fcm.googleapis.com/fcm/send/b",
                store
                    .current()
                    .record(session.origin)
                    ?.push
                    ?.endpoint,
            )
        }

    @Test
    fun `NT-02 idejo ID mode registers the inbox for its private messages, on by default`() =
        runBlocking {
            registrar
            val found = DiscoveryClient(http).discover(fake.address) as DiscoveryResult.Found
            val grant =
                TokenGrant(fake.tokenFor(FakePeople.Ada), tokenId = "tok_1", user = FakePeople.Ada)
            sessions.signedInToInbox(found, grant)
            val inbox = requireNotNull(store.current().inbox).origin

            eventually { distributor.registered.any { it.first == inbox } }
            registrar.onEndpoint(inbox, "https://fcm.googleapis.com/fcm/send/i", "BPUB", "AUTH")
            val id = requireNotNull(store.current().inbox?.push).registrationId

            // Private messages off: the inbox's registration ends, and a late endpoint goes back.
            AppChoices(store).privatePush(false)
            eventually { inbox in distributor.unregistered }
            assertNull(store.current().inbox?.push)
            assertTrue(
                fake.requests.any {
                    it.method == "DELETE" && it.url.encodedPath.endsWith("/me/push/unifiedpush/$id")
                },
            )
            distributor.unregistered.clear()
            registrar.onEndpoint(inbox, "https://fcm.googleapis.com/fcm/send/j", "B2", "A2")
            assertEquals(listOf(inbox), distributor.unregistered.toList())
            assertNull(store.current().inbox?.push)

            // On again: registered again.
            distributor.registered.clear()
            AppChoices(store).privatePush(true)
            eventually { distributor.registered.any { it.first == inbox } }
        }
}
