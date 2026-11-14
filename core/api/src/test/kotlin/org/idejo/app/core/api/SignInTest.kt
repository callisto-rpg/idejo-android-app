// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.api

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.ByteString.Companion.encodeUtf8
import org.idejo.app.core.api.ForumAddress.Parsed
import org.idejo.app.core.api.ForumAddress.Problem
import org.idejo.app.core.api.SignInRequest.Callback
import org.idejo.app.core.model.AccessToken
import org.idejo.app.core.model.ApiError
import org.idejo.app.core.model.OAuthInfo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SignInTest {
    private val server = MockWebServer()
    private val http = IdejoHttp.forLocalTesting()
    private val oauth =
        OAuthInfo(
            authorize = "/app/authorize",
            token = "/api/v1/oauth/token",
            revoke = "/api/v1/oauth/revoke",
            clientId = "org.idejo.app",
            redirectUri = "org.idejo.app:/oauth/callback",
            scopes = listOf("read", "write", "private", "moderate", "push"),
            codeChallengeMethods = listOf("S256"),
        )

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun valid(typed: String) = (ForumAddress.parse(typed) as Parsed.Valid).address

    @Test
    fun `SI-01 the scheme is optional, https assumed, the address reduced to its origin`() {
        assertEquals("https://garden.example.org/", valid("garden.example.org").toString())
        assertEquals(
            "https://garden.example.org/",
            valid("  Garden.Example.org/t/1/x?y#z ").toString(),
        )
        assertEquals(
            "https://garden.example.org:8443/",
            valid("https://garden.example.org:8443").toString(),
        )
    }

    @Test
    fun `SI-01 SY-01 https is required`() {
        assertEquals(
            Parsed.Invalid(Problem.NotHttps),
            ForumAddress.parse("http://garden.example.org"),
        )
        assertEquals(
            Parsed.Invalid(Problem.NotHttps),
            ForumAddress.parse("ftp://garden.example.org"),
        )
        assertEquals(Parsed.Invalid(Problem.Empty), ForumAddress.parse("  "))
        assertEquals(Parsed.Invalid(Problem.Malformed), ForumAddress.parse("garden"))
        assertEquals(Parsed.Invalid(Problem.Malformed), ForumAddress.parse("https://"))
    }

    @Test
    fun `SY-04 paths from the server must stay on the forum`() {
        val forum = valid("garden.example.org")
        assertEquals(
            "https://garden.example.org/app/authorize",
            forum.resolve("/app/authorize").toString(),
        )
        assertNull(forum.resolve("https://elsewhere.example.org/app/authorize"))
        assertNull(forum.resolve("//elsewhere.example.org/x"))
        assertNull(forum.resolve("http://garden.example.org/x"))
    }

    @Test
    fun `SI-03 the verifier and challenge follow RFC 7636`() {
        val verifier = Pkce.newVerifier()
        assertTrue(verifier.length in 43..128)
        assertTrue(verifier.all { it.isLetterOrDigit() || it in "-._~" })
        // RFC 7636 appendix B.
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
        assertTrue(Pkce.newState() != Pkce.newState())
    }

    @Test
    fun `SI-03 the authorize page asks for every scope with S256, state and the device's name`() {
        val request =
            SignInRequest(
                valid("garden.example.org"),
                oauth,
                verifier = "v".repeat(64),
                state = "s1",
            )
        val url = requireNotNull(request.authorizeUrl("Pixel 8"))
        assertEquals(
            "https://garden.example.org/app/authorize",
            url
                .newBuilder()
                .query(null)
                .build()
                .toString(),
        )
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals("org.idejo.app", url.queryParameter("client_id"))
        assertEquals("org.idejo.app:/oauth/callback", url.queryParameter("redirect_uri"))
        assertEquals("read write private moderate push", url.queryParameter("scope"))
        assertEquals("s1", url.queryParameter("state"))
        assertEquals(Pkce.challenge("v".repeat(64)), url.queryParameter("code_challenge"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertEquals("Pixel 8", url.queryParameter("device_name"))
        assertTrue(url.toString().contains("scope=read%20write"))
        assertNull(
            SignInRequest(
                valid("garden.example.org"),
                oauth.copy(authorize = "https://evil.example/a"),
            ).authorizeUrl("x"),
        )
    }

    @Test
    fun `SI-03 the redirect's state is checked and Cancel returns quietly`() {
        val request = SignInRequest(valid("garden.example.org"), oauth, state = "s1")
        assertEquals(
            Callback.Code("abc"),
            request.callback("org.idejo.app:/oauth/callback?code=abc&state=s1"),
        )
        assertEquals(
            Callback.WrongState,
            request.callback("org.idejo.app:/oauth/callback?code=abc&state=other"),
        )
        assertEquals(
            Callback.WrongState,
            request.callback("org.idejo.app:/oauth/callback?code=abc"),
        )
        assertEquals(
            Callback.Denied,
            request.callback("org.idejo.app:/oauth/callback?error=access_denied&state=s1"),
        )
        assertEquals(
            Callback.Error("server_error"),
            request.callback("org.idejo.app:/oauth/callback?error=server_error&state=s1"),
        )
        assertEquals(
            Callback.Invalid,
            request.callback("org.idejo.app:/elsewhere?code=abc&state=s1"),
        )
        assertEquals(Callback.Invalid, request.callback("org.idejo.app:/oauth/callback?state=s1"))
    }

    @Test
    fun `SI-04 the code is exchanged with the verifier, and the token is never printed`() =
        runTest {
            server.enqueue(json(payload("token.json")))
            val request = SignInRequest(server.address(), oauth, verifier = "v".repeat(64))
            val grant = (OAuthClient(http).exchange(request, "the-code") as ApiResult.Success).value

            assertEquals(AccessToken("idj_secret_token_value"), grant.accessToken)
            assertEquals(setOf("read", "write", "private", "moderate", "push"), grant.scopes)
            assertEquals("Ada Lovelace", grant.user?.displayName)
            assertFalse(grant.toString().contains("idj_secret"))

            val sent = server.takeRequest()
            assertEquals("POST", sent.method)
            assertEquals("/api/v1/oauth/token", sent.url.encodedPath)
            val form = requireNotNull(sent.body).utf8()
            for (field in listOf(
                "grant_type=authorization_code",
                "code=the-code",
                "client_id=org.idejo.app",
                "code_verifier=" + "v".repeat(64),
                "redirect_uri=org.idejo.app%3A%2Foauth%2Fcallback",
            )) {
                assertTrue(form, form.contains(field))
            }
            assertNull(sent.headers["Authorization"])
        }

    @Test
    fun `SI-04 a refused code gives the API's error`() =
        runTest {
            server.enqueue(error(400, ApiError.INVALID_GRANT, "The code has expired."))
            val result = OAuthClient(http).exchange(SignInRequest(server.address(), oauth), "old")
            val problem = (result as ApiResult.Failure).problem as ApiProblem.Server
            assertEquals(400, problem.status)
            assertEquals(ApiError.INVALID_GRANT, problem.error.code)
            assertEquals("The code has expired.", problem.error.message)
        }

    @Test
    fun `SI-11 signing out revokes the token as Bearer on its own forum`() =
        runTest {
            server.enqueue(json("", 204))
            assertTrue(
                OAuthClient(http).revoke(server.address(), oauth.revoke, AccessToken("idj_x")),
            )
            val sent = server.takeRequest()
            assertEquals("/api/v1/oauth/revoke", sent.url.encodedPath)
            assertEquals("Bearer idj_x", sent.headers["Authorization"])
            assertFalse(sent.url.toString().contains("idj_x"))
            assertFalse(
                OAuthClient(
                    http,
                ).revoke(
                    server.address(),
                    "https://elsewhere.example/revoke",
                    AccessToken("idj_x"),
                ),
            )
        }

    @Test
    fun `SY-01 the app's client refuses cleartext`() =
        runTest {
            server.enqueue(json("{}"))
            val client = IdejoHttp.newClient("idejo-test")
            val failed =
                runCatching {
                    client
                        .newCall(
                            okhttp3.Request
                                .Builder()
                                .url(server.url("/"))
                                .build(),
                        ).execute()
                        .close()
                }
            assertTrue(failed.exceptionOrNull() is java.net.UnknownServiceException)
        }

    @Test
    fun `the verifier's challenge is its SHA-256`() {
        assertEquals("abc".encodeUtf8().sha256().base64Url().trimEnd('='), Pkce.challenge("abc"))
        assertEquals(
            "https://garden.example.org/",
            "https://garden.example.org/".toHttpUrl().toString(),
        )
    }
}
