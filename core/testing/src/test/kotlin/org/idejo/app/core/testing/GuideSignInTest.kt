// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.testing

import kotlinx.coroutines.runBlocking
import org.idejo.app.core.api.ApiProblem
import org.idejo.app.core.api.ApiResult
import org.idejo.app.core.api.DiscoveryClient
import org.idejo.app.core.api.DiscoveryResult
import org.idejo.app.core.api.ForumApi
import org.idejo.app.core.api.IdejoHttp
import org.idejo.app.core.api.OAuthClient
import org.idejo.app.core.api.SignInRequest
import org.idejo.app.core.model.AccessToken
import org.idejo.app.core.model.ApiError
import org.idejo.app.core.model.TokenGrant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/** The API guide's sections 1 to 4, against the fake forum. */
class GuideSignInTest {
    private val fake = FakeIdejo()
    private val http = IdejoHttp.forLocalTesting()

    @After
    fun stop() = fake.close()

    private fun found(): DiscoveryResult.Found =
        runBlocking {
            DiscoveryClient(http).discover(fake.address) as DiscoveryResult.Found
        }

    private fun api(token: AccessToken?) = ForumApi(fake.address, "/api/v1", { token }, http)

    private fun failure(result: ApiResult<*>) =
        (result as ApiResult.Failure).problem as ApiProblem.Server

    /** Discovery, the Custom Tab where Ada taps Allow, and the exchange. */
    private fun signIn(): Pair<SignInRequest, TokenGrant> =
        runBlocking {
            val forum = found()
            val request = SignInRequest(forum.address, requireNotNull(forum.discovery.oauth))
            val redirect =
                requireNotNull(
                    fake.authorize(requireNotNull(request.authorizeUrl("Pixel 8")), FakePeople.Ada),
                )
            val code = (request.callback(redirect) as SignInRequest.Callback.Code).code
            request to (OAuthClient(http).exchange(request, code) as ApiResult.Success).value
        }

    @Test
    fun `SI-01 SI-03 SI-04 a member adds the forum by its address and signs in`() =
        runBlocking {
            val grant = signIn().second
            assertTrue(grant.accessToken.value.startsWith("idj_"))
            assertEquals(
                "Ada Lovelace",
                (api(grant.accessToken).me() as ApiResult.Success).value.displayName,
            )
        }

    @Test
    fun `SI-03 Cancel comes back as access_denied`() {
        val forum = found()
        val request = SignInRequest(forum.address, requireNotNull(forum.discovery.oauth))
        val redirect =
            fake.authorize(
                requireNotNull(request.authorizeUrl("Pixel 8")),
                FakePeople.Ada,
                allow = false,
            )
        assertEquals(SignInRequest.Callback.Denied, request.callback(requireNotNull(redirect)))
    }

    @Test
    fun `SI-03 a request without S256 gets no redirect`() {
        val forum = found()
        val url =
            requireNotNull(
                SignInRequest(
                    forum.address,
                    requireNotNull(forum.discovery.oauth),
                ).authorizeUrl("x"),
            )
        assertNull(
            fake.authorize(
                url.newBuilder().setQueryParameter("code_challenge_method", "plain").build(),
                FakePeople.Ada,
            ),
        )
    }

    @Test
    fun `SI-04 a code needs its verifier, works once, and lasts 60 seconds`() =
        runBlocking {
            val forum = found()
            val oauth = requireNotNull(forum.discovery.oauth)

            fun code(request: SignInRequest) =
                (
                    request.callback(
                        requireNotNull(
                            fake.authorize(
                                requireNotNull(request.authorizeUrl("x")),
                                FakePeople.Ada,
                            ),
                        ),
                    ) as SignInRequest.Callback.Code
                ).code

            val request = SignInRequest(forum.address, oauth)
            val code = code(request)
            val wrong = request.copy(verifier = "w".repeat(64))
            assertEquals(
                ApiError.INVALID_GRANT,
                failure(OAuthClient(http).exchange(wrong, code)).error.code,
            )

            val late = SignInRequest(forum.address, oauth)
            val lateCode = code(late)
            fake.clock.advanceSeconds(61)
            assertEquals(
                ApiError.INVALID_GRANT,
                failure(OAuthClient(http).exchange(late, lateCode)).error.code,
            )
        }

    @Test
    fun `SI-04 a code used twice is refused and its token revoked`() =
        runBlocking {
            val forum = found()
            val request = SignInRequest(forum.address, requireNotNull(forum.discovery.oauth))
            val redirect =
                requireNotNull(
                    fake.authorize(requireNotNull(request.authorizeUrl("x")), FakePeople.Ada),
                )
            val code = (request.callback(redirect) as SignInRequest.Callback.Code).code
            val token =
                (
                    OAuthClient(
                        http,
                    ).exchange(request, code) as ApiResult.Success
                ).value.accessToken
            assertEquals(
                ApiError.INVALID_GRANT,
                failure(OAuthClient(http).exchange(request, code)).error.code,
            )
            assertTrue(failure(api(token).me()).signedOut)
        }

    @Test
    fun `SI-11 SI-09 a revoked token is signed out`() =
        runBlocking {
            val grant = signIn().second
            assertTrue(
                OAuthClient(http).revoke(fake.address, "/api/v1/oauth/revoke", grant.accessToken),
            )
            assertTrue(failure(api(grant.accessToken).me()).signedOut)
        }

    @Test
    fun `SI-05 a visitor reads without a token, and is not signed out`() =
        runBlocking {
            val visitor = api(null)
            assertEquals(
                listOf(
                    "Vegetables",
                ),
                (visitor.categories() as ApiResult.Success).value.map {
                    it.title
                },
            )
            val me = failure(visitor.me())
            assertEquals(ApiError.UNAUTHENTICATED, me.error.code)
            assertFalse(me.signedOut)
            assertTrue(fake.requests.none { it.headers["Authorization"] != null })
        }

    @Test
    fun `SI-02 the administrators turned the API off`() =
        runBlocking {
            fake.apiOff = true
            assertEquals(
                DiscoveryResult.AppTurnedOff("Garden club"),
                DiscoveryClient(http).discover(fake.address),
            )
            assertEquals(404, failure(api(null).server()).status)
        }

    @Test
    fun `pages, hidden categories, and errors in the API's one shape`() =
        runBlocking {
            val api = api(fake.tokenFor(FakePeople.Ada))
            assertEquals(ApiError.NOT_FOUND, failure(api.category("c_hidden")).error.code)
            val page = (api.categoryTopics("c_veg", limit = 1) as ApiResult.Success).value
            assertEquals("Tomato plans for spring", page.data.single().title)
            assertNull(page.nextCursor)

            fake.failNext(429, ApiError.RATE_LIMITED, "Too many requests.", retryAfterSeconds = 7)
            assertEquals(Duration.ofSeconds(7), failure(api.server()).retryAfter)
        }
}
