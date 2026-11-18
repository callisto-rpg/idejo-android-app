// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.testing

import kotlinx.coroutines.runBlocking
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.ForumApi
import org.idejo.app.core.api.IdejoClient
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.model.NotificationSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Profile and account's lists and choices (ST-03, ST-05, ST-08, NT-08), against the fake. */
class GuideSettingsTest {
    private val fake = FakeIdejo()
    private val token = fake.tokenFor(FakePeople.Ada, "Pixel 8")
    private val api = ForumApi(fake.address, "/api/v1", { token }, IdejoHttp.forLocalTesting())

    @After
    fun stop() = fake.close()

    private fun <T> ApiResult<T>.value(): T = (this as ApiResult.Success).value

    @Test
    fun `ST-05 the signed-in devices are listed, this one marked`() =
        runBlocking {
            val tokens = api.tokens().value()
            val device = tokens.single()
            assertEquals("Pixel 8", device.name)
            assertEquals(IdejoClient.SCOPES, device.scopes)
            assertEquals(fake.clock.now, device.signedInAt)
            assertTrue(device.current)
        }

    @Test
    fun `ST-05 removing a device signs it out`() =
        runBlocking {
            val device = api.tokens().value().single()
            api.deleteToken(device.id).value()
            val problem = (api.me() as ApiResult.Failure).problem as ApiProblem.Server
            assertTrue(problem.signedOut)
        }

    @Test
    fun `ST-03 the notification choices are read`() =
        runBlocking {
            val settings = api.notificationSettings().value()
            assertEquals(true, settings.email?.mentions)
            assertEquals(false, settings.email?.roleMentions)
            assertEquals(true, settings.email?.ownTopics)
            assertEquals(true, settings.email?.onceAWeek)
            assertEquals(true, settings.push?.reply)
            assertEquals(true, settings.push?.privateMessages)
        }

    @Test
    fun `ST-03 a changed notification choice is sent alone`() =
        runBlocking {
            val saved =
                api
                    .updateNotificationSettings(
                        NotificationSettings(push = NotificationSettings.Push(reply = false)),
                    ).value()
            assertEquals(
                """{"push":{"reply":false}}""",
                requireNotNull(fake.requests.last().body).utf8(),
            )
            assertEquals(false, saved.push?.reply)
            assertEquals(true, saved.push?.mention)
            assertEquals(true, saved.email?.mentions)
        }

    @Test
    fun `ST-08 the ignored members are listed, and unignoring removes them`() =
        runBlocking {
            val leo = requireNotNull(FakePeople.Leo.id)
            api.ignore(leo, ignored = true).value()
            assertEquals(listOf("Leo Moreau"), api.ignored().value().map { it.displayName })

            api.ignore(leo, ignored = false).value()
            assertTrue(api.ignored().value().isEmpty())
        }

    @Test
    fun `NT-08 the push registrations are listed`() =
        runBlocking {
            val registration = fake.pushRegistration(FakePeople.Ada, "Pixel 8", token)
            val listed = api.pushRegistrations().value().single()
            assertEquals(registration.id, listed.id)
            assertEquals("unifiedpush", listed.kind)
            assertEquals("Pixel 8", listed.name)
            assertEquals("ntfy.example.net", listed.endpointHost)
            assertEquals(fake.clock.now, listed.createdAt)
            assertTrue(listed.thisDevice)
        }

    @Test
    fun `NT-08 removing a push registration ends it`() =
        runBlocking {
            val registration = fake.pushRegistration(FakePeople.Ada, "Pixel 8", token)
            api.deletePushRegistration(registration.id).value()
            assertTrue(api.pushRegistrations().value().isEmpty())
        }

    @Test
    fun `NT-08 a test notification goes to every device`() =
        runBlocking {
            fake.pushRegistration(FakePeople.Ada, "Pixel 8", token)
            fake.pushRegistration(FakePeople.Ada, "Laptop")
            assertEquals(2, api.testPush().value().queued)
        }
}
