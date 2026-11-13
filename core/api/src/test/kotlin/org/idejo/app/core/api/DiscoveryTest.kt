// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.api

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiscoveryTest {
    private val server = MockWebServer()
    private val client = DiscoveryClient(IdejoHttp.forTesting())

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private suspend fun discover(response: MockResponse): DiscoveryResult {
        server.enqueue(response)
        return client.discover(server.address())
    }

    @Test
    fun `SI-01 SY-07 an idejo forum is read, unknown fields ignored`() =
        runTest {
            val found = discover(json(payload("discovery.json"))) as DiscoveryResult.Found
            assertEquals("Garden club", found.discovery.name)
            assertEquals("/api/v1/socket", found.discovery.api?.socket)
            assertEquals("/app/authorize", found.discovery.oauth?.authorize)
            assertEquals(null, found.discovery.community?.directoryUrl)
            assertEquals(20, found.discovery.limits?.devicesPerUser)
            assertTrue("polls" in found.discovery.features)
            assertEquals("/.well-known/idejo", server.takeRequest().url.encodedPath)
        }

    @Test
    fun `SI-02 no api means not an idejo forum or too old`() =
        runTest {
            assertEquals(
                DiscoveryResult.NotIdejo,
                discover(json("""{"name": "Old", "url": "https://old.example/"}""")),
            )
            assertEquals(DiscoveryResult.NotIdejo, discover(json("<html>Hello</html>")))
            assertEquals(DiscoveryResult.NotIdejo, discover(json("[]")))
            assertEquals(DiscoveryResult.NotIdejo, discover(json("{}", 404)))
        }

    @Test
    fun `SI-02 api null means the administrators turned the app off`() =
        runTest {
            val result =
                discover(
                    json(
                        """{"name": "Garden club", "url": "https://garden.example.org/",
                        |"api": null}
                        """.trimMargin(),
                    ),
                )
            assertEquals(DiscoveryResult.AppTurnedOff("Garden club"), result)
        }

    @Test
    fun `SI-02 versions without v1 need a newer app`() =
        runTest {
            val body = payload("discovery.json").replace("\"v1\"", "\"v2\"")
            assertEquals(DiscoveryResult.NeedsNewerApp("Garden club"), discover(json(body)))
        }

    @Test
    fun `SI-01 a server error or no answer is unreachable, not a verdict`() =
        runTest {
            assertTrue(discover(json("", 503)) is DiscoveryResult.Unreachable)
            server.close()
            assertTrue(client.discover(server.address()) is DiscoveryResult.Unreachable)
        }
}
