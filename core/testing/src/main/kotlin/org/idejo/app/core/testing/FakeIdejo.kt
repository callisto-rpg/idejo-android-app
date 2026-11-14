// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.testing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.idejo.app.core.api.ForumAddress
import org.idejo.app.core.api.IdejoClient
import org.idejo.app.core.api.IdejoJson
import org.idejo.app.core.api.Pkce
import org.idejo.app.core.model.AccessToken
import org.idejo.app.core.model.ApiError
import org.idejo.app.core.model.Category
import org.idejo.app.core.model.Changes
import org.idejo.app.core.model.Commit
import org.idejo.app.core.model.CommitResult
import org.idejo.app.core.model.ComposerState
import org.idejo.app.core.model.Discovery
import org.idejo.app.core.model.ErrorBody
import org.idejo.app.core.model.LatestMessage
import org.idejo.app.core.model.ListBody
import org.idejo.app.core.model.Me
import org.idejo.app.core.model.MentionState
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.MessageState
import org.idejo.app.core.model.MessageWindow
import org.idejo.app.core.model.Page
import org.idejo.app.core.model.Person
import org.idejo.app.core.model.SearchHit
import org.idejo.app.core.model.Server
import org.idejo.app.core.model.ServerSettings
import org.idejo.app.core.model.Tag
import org.idejo.app.core.model.TokenGrant
import org.idejo.app.core.model.Topic
import org.idejo.app.core.model.TopicPreview
import org.idejo.app.core.model.Unread
import org.idejo.app.core.model.UnreadCounter
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant

/**
 * An idejo forum in the test's own process (NF-08), behaving as the API guide describes:
 * discovery (§1), sign-in with PKCE (§2), tokens and revocation (§3), the error shape and
 * pages (§4), the growing message (§6), events and catch-up (§7). Its time is [clock].
 * Not idejo: its rules are the guide's, simplified where the guide does not say more.
 *
 * ```
 * FakeIdejo().use { fake ->
 *     val token = fake.tokenFor(FakePeople.Ada)
 *     val api = ForumApi(fake.address, "/api/v1", { token }, IdejoHttp.forLocalTesting())
 * }
 * ```
 */
@Suppress("TooManyFunctions", "LargeClass") // One handler per endpoint: the API, faked.
class FakeIdejo(
    val clock: FakeClock = FakeClock(),
    val settings: FakeSettings = FakeSettings(),
) : AutoCloseable {
    private val server = MockWebServer()
    private val lock = Any()
    private val random = SecureRandom()
    private var nextId = 100L

    private val categories = seedCategories().toMutableList()
    private val topics = linkedMapOf("t_1" to seedTopic(clock.now))
    private val readers = mutableMapOf<String, Reader>()
    private val composers = mutableMapOf<Pair<String, String>, ComposerRecord>()
    private val tokens = mutableMapOf<String, Person>()
    private val codes = mutableMapOf<String, PendingCode>()
    internal val sockets = mutableListOf<FakeSocket>()
    private val failures = ArrayDeque<MockResponse>()

    /** Off: discovery says `api: null`, every API path 404, the socket 404 (API guide intro). */
    @Volatile var apiOff = false

    /** Every request the app made, in order. */
    val requests: MutableList<RecordedRequest> =
        java.util.Collections.synchronizedList(
            mutableListOf(),
        )

    init {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    return synchronized(lock) { failures.removeFirstOrNull() ?: route(request) }
                }
            }
        server.start()
    }

    /** The forum's address (http, for tests: `ForumAddress.forLocalTesting`). */
    val address: ForumAddress get() = ForumAddress.forLocalTesting(server.url("/"))

    val url: HttpUrl get() = server.url("/")

    override fun close() {
        synchronized(lock) { sockets.toList() }.forEach { it.close() }
        server.close()
    }

    // What a test can do to the forum.

    /** A token for [person], as if they had signed in. */
    fun tokenFor(person: Person): AccessToken = synchronized(lock) { newToken(person) }

    /**
     * Revokes [token] as **Apps and devices** would:
     * 401 from then on, `token.revoked` on its sockets.
     */
    fun revoke(token: AccessToken) = synchronized(lock) { revokeLocked(token.value) }

    /** The next request gets [status] with the API's error shape. */
    fun failNext(
        status: Int,
        code: String,
        message: String = "",
        retryAfterSeconds: Long? = null,
    ) = synchronized(lock) {
        failures +=
            error(status, code, message).let { response ->
                if (retryAfterSeconds == null) {
                    response
                } else {
                    response
                        .newBuilder()
                        .addHeader("Retry-After", retryAfterSeconds.toString())
                        .build()
                }
            }
    }

    /**
     * The forum's sign-in page in a Custom Tab, where [person] taps **Allow** (or **Cancel**):
     * the redirect the browser is sent to. An invalid request returns null, as the page
     * shows an error and never redirects (API guide §2).
     */
    fun authorize(
        url: HttpUrl,
        person: Person,
        allow: Boolean = true,
    ): String? =
        synchronized(lock) {
            val valid =
                url.encodedPath == "/app/authorize" &&
                    url.queryParameter("response_type") == "code" &&
                    url.queryParameter("client_id") == IdejoClient.ID &&
                    url.queryParameter("redirect_uri") == IdejoClient.REDIRECT_URI &&
                    url.queryParameter("code_challenge_method") == "S256" &&
                    url.queryParameter("code_challenge")?.length == CHALLENGE_LENGTH
            if (!valid) return@synchronized null
            val state = url.queryParameter("state").orEmpty()
            val redirect = IdejoClient.REDIRECT_URI + "?"
            if (!allow) return@synchronized redirect + "error=access_denied&state=" + encode(state)
            val code = randomId()
            codes[code] =
                PendingCode(person, requireNotNull(url.queryParameter("code_challenge")), clock.now)
            redirect + "code=" + code + "&state=" + encode(state)
        }

    /** [person] writes [text] in [topicId] as one closed message, as another member would. */
    fun post(
        person: Person,
        topicId: String,
        text: String,
    ): Message =
        synchronized(lock) {
            val record = requireNotNull(topics[topicId])
            // Through the same rules as the app's own Enters (the full stop included).
            val message =
                openMessage(record, person, punctuate(text)).let {
                    close(record, it.id, held = false)
                }
            news(record, person)
            message
        }

    /** A category added to the forum, as an administrator would. */
    fun addCategory(category: Category) = synchronized(lock) { categories += category }

    /** A topic [author] started in [categoryId], with its opening message. */
    fun addTopic(
        title: String,
        categoryId: String = "c_veg",
        author: Person = FakePeople.Leo,
        tags: List<Tag> = emptyList(),
    ): Topic =
        synchronized(lock) {
            val id = "t_" + nextId++
            val topic =
                Topic(
                    id = id,
                    title = title,
                    slug = title.lowercase().replace(' ', '-'),
                    url = url.resolve("/t/$id")?.toString(),
                    categoryId = categoryId,
                    author = author,
                    createdAt = clock.now,
                    lastActivityAt = clock.now,
                    tags = tags,
                )
            topics[id] = TopicRecord(topic)
            openMessage(topics.getValue(id), author, title).let {
                close(topics.getValue(id), it.id, held = false)
            }
            topic
        }

    /** [person] follows [topicId], as after writing there. */
    fun follow(
        person: Person,
        topicId: String,
    ) = synchronized(lock) { reader(person).followed += topicId }

    /** What [person] has not read in [topicId]: [count] messages, the strongest [state]. */
    fun unread(
        person: Person,
        topicId: String,
        count: Int,
        state: MentionState = MentionState.New,
    ) = synchronized(lock) {
        val unread = reader(person).unread
        if (count == 0) unread.remove(topicId) else unread[topicId] = Unseen(count, state)
        pushUnread(person)
    }

    /** Whether [person] follows [topicId] on the server. */
    fun follows(
        person: Person,
        topicId: String,
    ): Boolean = synchronized(lock) { topicId in reader(person).followed }

    /** Whether [person] muted [topicId] on the server. */
    fun mutes(
        person: Person,
        topicId: String,
    ): Boolean = synchronized(lock) { topicId in reader(person).muted }

    /** Whether a socket of the app joined [channel] (`category:c_veg`, `user:me`…). */
    fun joined(channel: String): Boolean =
        synchronized(lock) { sockets.any { it.isJoined(channel) } }

    /** A topic's messages as the server has them. */
    fun messages(topicId: String): List<Message> =
        synchronized(lock) { topics.getValue(topicId).messages.toList() }

    // Routing.

    private fun route(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        if (path == "/.well-known/idejo") return discovery()
        if (path == "/api/v1/socket/websocket") return socketUpgrade(request)
        if (!path.startsWith(API)) return status(HTTP_NOT_FOUND)
        if (apiOff) return error(HTTP_NOT_FOUND, ApiError.NOT_FOUND, "Not found.")
        val route = path.removePrefix(API)
        return when {
            route == "/oauth/token" && request.method == "POST" -> tokenGrant(request)
            route == "/oauth/revoke" && request.method == "POST" -> revokeRequest(request)
            else -> authenticated(request, route)
        }
    }

    private fun authenticated(
        request: RecordedRequest,
        route: String,
    ): MockResponse {
        val header = request.headers["Authorization"]
        val viewer =
            if (header == null) {
                null
            } else {
                tokens[header.removePrefix("Bearer ")]
                    ?: return error(HTTP_UNAUTHORIZED, ApiError.INVALID_TOKEN, "Sign in again.")
            }
        val parts = route.trim('/').split('/')
        for (candidate in routes) {
            val params = candidate.match(request.method.orEmpty(), parts) ?: continue
            return candidate.handle(Call(request, viewer, params))
        }
        return notFound()
    }

    /** A request as a route sees it: who asks, and the path's `{params}`. */
    private class Call(
        val request: RecordedRequest,
        val viewer: Person?,
        val params: Map<String, String>,
    ) {
        operator fun get(name: String): String = params.getValue(name)
    }

    private class Route(
        val method: String,
        pattern: String,
        val handle: (Call) -> MockResponse,
    ) {
        private val pattern = pattern.split('/')

        fun match(
            method: String,
            parts: List<String>,
        ): Map<String, String>? {
            if (method != this.method || parts.size != pattern.size) return null
            val params = mutableMapOf<String, String>()
            for ((expected, actual) in pattern.zip(parts)) {
                when {
                    expected.startsWith("{") -> params[expected.trim('{', '}')] = actual
                    expected != actual -> return null
                }
            }
            return params
        }
    }

    /** The endpoints the fake answers; anything else is 404. Order matters: fixed before `{id}`. */
    private val routes: List<Route> =
        listOf(
            Route("GET", "server") { ok(server()) },
            Route("GET", "me") { call -> call.viewer?.let { ok(me(it)) } ?: unauthenticated() },
            Route("GET", "me/communities") { ok(ListBody<Topic>(emptyList())) },
            Route("GET", "categories") { ok(ListBody(categories.filter { it.permissions.view })) },
            Route(
                "GET",
                "categories/{id}",
            ) { call -> category(call["id"])?.let(::ok) ?: notFound() },
            Route(
                "GET",
                "categories/{id}/topics",
            ) { call -> categoryTopics(call["id"], call.request) },
            Route("GET", "topics/active") { call ->
                val expanded = call.request.expanded
                ok(ListBody(topics.values.map { it.seen(call.viewer, expanded) }))
            },
            Route("GET", "tags") {
                val all = topics.values.flatMap { it.topic.tags }.distinctBy { it.id }
                ok(ListBody(all))
            },
            Route("GET", "topics") { call -> tagged(call.viewer, call.request) },
            Route("GET", "search") { call -> search(call.viewer, call.request) },
            Route("GET", "unread") { call ->
                call.viewer?.let { ok(unreadOf(it)) }
                    ?: unauthenticated()
            },
            Route("GET", "topics/{id}") { call ->
                topics[call["id"]]?.let { ok(it.topic.seenBy(call.viewer)) }
                    ?: notFound()
            },
            Route("PUT", "topics/{id}/follow") { call ->
                reading(call) { reader, id ->
                    reader.followed += id
                    reader.muted -= id
                }
            },
            Route("DELETE", "topics/{id}/follow") { call ->
                reading(call) { reader, id -> reader.followed -= id }
            },
            Route("PUT", "topics/{id}/mute") { call ->
                reading(call) { reader, id ->
                    reader.muted += id
                    reader.followed -= id
                    reader.unread -= id
                }
            },
            Route("DELETE", "topics/{id}/mute") { call ->
                reading(call) { reader, id -> reader.muted -= id }
            },
            Route("POST", "topics/{id}/mark-unread") { call ->
                reading(call) { reader, id ->
                    reader.unread[id] = reader.unread[id] ?: Unseen(1, MentionState.New)
                }
            },
            Route("GET", "topics/{id}/messages") { call -> window(call["id"]) },
            Route("GET", "topics/{id}/changes") { call -> changes(call["id"], call.request) },
            Route("GET", "topics/{id}/pins") { call -> pins(call["id"]) },
            Route(
                "GET",
                "messages/{id}",
            ) { call -> findMessage(call["id"])?.let(::ok) ?: notFound() },
            Route(
                "GET",
                "topics/{id}/composer",
            ) { call -> composing(call) { record, person -> ok(composerState(record, person)) } },
            Route(
                "POST",
                "topics/{id}/composer/typing",
            ) { call -> composing(call) { record, person -> ok(typing(record, person)) } },
            Route("POST", "topics/{id}/composer/commit") { call ->
                composing(call) { record, person -> commit(record, person, call.request) }
            },
        )

    /** A change to what the viewer follows, mutes or has read in a topic: the topic after. */
    private fun reading(
        call: Call,
        change: (Reader, String) -> Unit,
    ): MockResponse {
        val record = topics[call["id"]] ?: return notFound()
        val person = call.viewer ?: return unauthenticated()
        change(reader(person), record.topic.id)
        pushUnread(person)
        return ok(record.topic.seenBy(person))
    }

    private fun reader(person: Person): Reader =
        readers.getOrPut(requireNotNull(person.id)) {
            Reader()
        }

    /** The topic as [viewer] sees it: followed, muted, unread. */
    private fun Topic.seenBy(viewer: Person?): Topic {
        val reader = viewer?.let(::reader) ?: return this
        val unseen = reader.unread[id]
        return copy(
            followed = id in reader.followed,
            muted = id in reader.muted,
            unreadCount = unseen?.count ?: 0,
            mentionState = unseen?.state ?: MentionState.None,
        )
    }

    /** `GET /unread` (BR-06): followed topics with news, then the others. */
    private fun unreadOf(person: Person): Unread {
        val reader = reader(person)
        val seen =
            topics.values
                .map { it.topic.seenBy(person) }
                .filter { it.unreadCount > 0 && !it.muted }
        val (following, others) = seen.partition { it.followed }
        return Unread(following, Page(others), counterOf(reader))
    }

    /** The **Unread** button: replies and mentions counted, the strongest kind named. */
    private fun counterOf(reader: Reader): UnreadCounter {
        val strong = reader.unread.values.filter { it.state > MentionState.New }
        val level =
            reader.unread.values
                .maxOfOrNull { it.state }
                ?.takeIf { it > MentionState.None }
        return UnreadCounter(strong.sumOf { it.count }, level?.let(::levelName))
    }

    private fun levelName(state: MentionState): String =
        when (state) {
            MentionState.None, MentionState.New -> "new"
            MentionState.Role -> "role"
            MentionState.Reply -> "reply"
            MentionState.Mention -> "mention"
        }

    /**
     * A message by [author] in [record]: new for those who follow the topic, and
     * `topic.activity` on its category's channel (API guide §7).
     */
    private fun news(
        record: TopicRecord,
        author: Person,
    ) {
        readers.forEach { (id, reader) ->
            if (id == author.id || record.topic.id !in reader.followed) return@forEach
            val seen = reader.unread[record.topic.id]
            reader.unread[record.topic.id] =
                Unseen(
                    (seen?.count ?: 0) + 1,
                    maxOf(seen?.state ?: MentionState.New, MentionState.New),
                )
            FakePeople.all.firstOrNull { it.id == id }?.let(::pushUnread)
        }
        val payload =
            buildJsonObject {
                put("topic_id", record.topic.id)
                put("last_activity_at", clock.now.toString())
                put("messages_count", record.messages.size)
            }
        sockets.forEach {
            it.push(
                "category:" + record.topic.categoryId,
                "topic.activity",
                payload,
            )
        }
    }

    /** A category channel's join: the category must be visible. */
    internal fun joinCategory(id: String): JsonObject? =
        synchronized(lock) { category(id)?.let { JsonObject(emptyMap()) } }

    /** `unread.changed` on [person]'s user:me channels. */
    private fun pushUnread(person: Person) {
        val counter = counterOf(reader(person))
        val payload =
            buildJsonObject {
                put("count", counter.count)
                put("level", counter.level)
            }
        socketsOf(person).forEach { it.push("user:me", "unread.changed", payload) }
    }

    private fun socketsOf(person: Person): List<FakeSocket> =
        sockets.filter { tokens[it.token]?.id == person.id }

    private fun authenticatedViewer(request: RecordedRequest): Person? =
        request.headers["Authorization"]?.removePrefix("Bearer ")?.let { tokens[it] }

    private fun composing(
        call: Call,
        handle: (TopicRecord, Person) -> MockResponse,
    ): MockResponse {
        val record = topics[call["id"]] ?: return notFound()
        val person = call.viewer ?: return unauthenticated()
        return handle(record, person)
    }

    private fun pins(topicId: String): MockResponse {
        val record = topics[topicId] ?: return notFound()
        return ok(ListBody(record.messages.filter { it.pinnedAt != null }))
    }

    // §1 Discovery.

    private fun discovery(): MockResponse {
        if (apiOff) {
            return ok(
                buildJsonObject {
                    put("name", settings.name)
                    put("url", url.toString())
                    put("api", null as String?)
                },
            )
        }
        return ok(
            Discovery(
                name = settings.name,
                url = url.toString(),
                idejoVersion = "0.3.12",
                logoUrl = url.resolve("/logo").toString(),
                api =
                    org.idejo.app.core.model.ApiInfo(
                        listOf("v1"),
                        API,
                        "$API/openapi.json",
                        "$API/socket",
                    ),
                oauth =
                    org.idejo.app.core.model.OAuthInfo(
                        authorize = "/app/authorize",
                        token = "$API/oauth/token",
                        revoke = "$API/oauth/revoke",
                        clientId = IdejoClient.ID,
                        redirectUri = IdejoClient.REDIRECT_URI,
                        scopes = IdejoClient.SCOPES,
                        codeChallengeMethods = listOf("S256"),
                    ),
                features = listOf("polls"),
            ),
        )
    }

    // §2, §3 Sign-in and tokens.

    private fun tokenGrant(request: RecordedRequest): MockResponse {
        val form = form(request)
        if (form["grant_type"] != "authorization_code") {
            return error(HTTP_BAD_REQUEST, "unsupported_grant_type", "Only authorization codes.")
        }
        if (form["client_id"] != IdejoClient.ID ||
            form["redirect_uri"] != IdejoClient.REDIRECT_URI
        ) {
            return error(HTTP_BAD_REQUEST, "invalid_client", "Unknown client.")
        }
        val code = form["code"].orEmpty()
        val pending =
            codes[code]
                ?: return error(HTTP_BAD_REQUEST, ApiError.INVALID_GRANT, "The code is not valid.")
        if (pending.used) {
            // A code used twice: refused, and the token it gave is revoked.
            pending.token?.let(::revokeLocked)
            return error(HTTP_BAD_REQUEST, ApiError.INVALID_GRANT, "The code was already used.")
        }
        pending.used = true
        val expired = Duration.between(pending.at, clock.now) > CODE_LIFETIME
        val verified = Pkce.challenge(form["code_verifier"].orEmpty()) == pending.challenge
        if (expired ||
            !verified
        ) {
            return error(HTTP_BAD_REQUEST, ApiError.INVALID_GRANT, "The code is not valid.")
        }
        val token = newToken(pending.person)
        pending.token = token.value
        return ok(
            TokenGrant(
                token,
                scope = IdejoClient.SCOPES.joinToString(" "),
                tokenId =
                    "tok_" + randomId(),
                user = pending.person,
            ),
        )
    }

    private fun revokeRequest(request: RecordedRequest): MockResponse {
        request.headers["Authorization"]?.removePrefix("Bearer ")?.let(::revokeLocked)
        form(request)["token"]?.let(::revokeLocked)
        return status(HTTP_NO_CONTENT)
    }

    private fun newToken(person: Person): AccessToken =
        AccessToken("idj_" + randomId()).also {
            tokens[it.value] =
                person
        }

    private fun revokeLocked(token: String) {
        if (tokens.remove(token) == null) return
        sockets.filter { it.token == token }.forEach { it.revoked() }
    }

    // Reading.

    private fun server(): Server =
        Server(
            name = settings.name,
            url = url.toString(),
            theme = settings.theme,
            settings =
                ServerSettings(
                    windowSeconds = settings.windowSeconds,
                    unansweredSeconds = settings.unansweredSeconds,
                    earlyReply = settings.earlyReply,
                ),
        )

    private fun me(person: Person): Me =
        Me(
            id = requireNotNull(person.id),
            displayName = person.displayName,
            username = person.username,
            verified = true,
        )

    private fun category(id: String): Category? =
        categories.firstOrNull {
            it.id == id &&
                it.permissions.view
        }

    private fun categoryTopics(
        id: String,
        request: RecordedRequest,
    ): MockResponse {
        category(id) ?: return notFound()
        val viewer = authenticatedViewer(request)
        val all =
            topics.values
                .map { it.seen(viewer, request.expanded) }
                .filter { it.categoryId == id }
        return ok(paged(all, request))
    }

    /** `GET /topics?tags=a,b&operator=or` (BR-04): topics carrying all, or any, of the tags. */
    private fun tagged(
        viewer: Person?,
        request: RecordedRequest,
    ): MockResponse {
        val wanted =
            request.url
                .queryParameter("tags")
                .orEmpty()
                .split(',')
                .filter { it.isNotBlank() }
                .toSet()
        val any = request.url.queryParameter("operator") == "or"
        val all =
            topics.values.map { it.topic.seenBy(viewer) }.filter { topic ->
                val carried = topic.tags.mapNotNull { it.slug }.toSet()
                if (any) carried.any { it in wanted } else carried.containsAll(wanted)
            }
        return ok(paged(all, request))
    }

    /** `GET /search` (BR-05): messages whose words or topic title hold the query, marked. */
    private fun search(
        viewer: Person?,
        request: RecordedRequest,
    ): MockResponse {
        val query =
            request.url
                .queryParameter("q")
                .orEmpty()
                .trim()
        val category = request.url.queryParameter("category_id")
        val hits =
            topics.values
                .map { it.topic.seenBy(viewer) to it.messages }
                .filter { (topic, _) -> category == null || topic.categoryId == category }
                .flatMap { (topic, messages) ->
                    messages
                        .filter {
                            query.isNotEmpty() &&
                                it.bodyMd.orEmpty().contains(
                                    query,
                                    ignoreCase = true,
                                )
                        }.map { message ->
                            SearchHit(
                                messageId = message.id,
                                topicId = topic.id,
                                topicTitle = topic.title,
                                categoryId = topic.categoryId,
                                authorName = message.author.displayName,
                                excerpt = marked(message.bodyMd.orEmpty(), query),
                            )
                        }
                }
        return ok(paged(hits, request))
    }

    /** [text] cut where [query] stands, each match marked. */
    private fun marked(
        text: String,
        query: String,
    ): List<SearchHit.Segment> {
        val segments = mutableListOf<SearchHit.Segment>()
        var from = 0
        while (true) {
            val at = text.indexOf(query, from, ignoreCase = true)
            if (at < 0) break
            if (at > from) segments += SearchHit.Segment(text.substring(from, at))
            segments += SearchHit.Segment(text.substring(at, at + query.length), match = true)
            from = at + query.length
        }
        if (from < text.length) segments += SearchHit.Segment(text.substring(from))
        return segments
    }

    /** One page of [all] after the request's cursor, at most its `limit`. */
    private fun <T> paged(
        all: List<T>,
        request: RecordedRequest,
    ): Page<T> {
        val limit =
            request.url
                .queryParameter("limit")
                ?.toIntOrNull()
                ?.coerceIn(1, MAX_PAGE)
                ?: DEFAULT_PAGE
        val from = request.url.queryParameter("cursor")?.let(::decodeCursor) ?: 0
        val page = all.drop(from).take(limit)
        val next = (from + page.size).takeIf { it < all.size }?.let(::encodeCursor)
        return Page(page, nextCursor = next, prevCursor = null)
    }

    /** The topic as [viewer] sees it, with its preview when the list asked for one. */
    private fun TopicRecord.seen(
        viewer: Person?,
        expanded: Boolean,
    ): Topic {
        val seen = topic.seenBy(viewer)
        if (!expanded) return seen
        val writers = messages.map { it.author }.distinctBy { it.id }
        return seen.copy(
            preview =
                TopicPreview(
                    firstExcerpt = messages.firstOrNull()?.bodyMd,
                    latest =
                        messages.drop(1).takeLast(PREVIEW_LATEST).map {
                            LatestMessage(it.id, it.author.displayName, it.bodyMd)
                        },
                    writers = writers,
                    writersCount = writers.size,
                ),
        )
    }

    private val RecordedRequest.expanded: Boolean
        get() = url.queryParameter("expanded") == "true"

    private fun window(topicId: String): MockResponse {
        val record = topics[topicId] ?: return notFound()
        return ok(
            MessageWindow(
                data = record.messages.toList(),
                firstMessage = record.messages.firstOrNull(),
                cursor = record.changeSeq,
                total = record.messages.size,
            ),
        )
    }

    private fun changes(
        topicId: String,
        request: RecordedRequest,
    ): MockResponse {
        val record = topics[topicId] ?: return notFound()
        val since =
            request.url
                .queryParameter("since")
                ?.toLongOrNull()
                ?.takeIf { it >= 0 }
                ?: return error(HTTP_BAD_REQUEST, "bad_cursor", "That cursor is not valid.")
        if (since >
            record.changeSeq
        ) {
            return error(HTTP_GONE, ApiError.CURSOR_EXPIRED, "Load the topic again.")
        }
        val limit =
            request.url
                .queryParameter("limit")
                ?.toIntOrNull()
                ?.coerceIn(1, MAX_PAGE)
                ?: DEFAULT_PAGE
        val changed = record.messages.filter { it.cursor > since }.sortedBy { it.cursor }
        val page = changed.take(limit)
        val cursor = page.lastOrNull()?.cursor ?: since
        return ok(Changes(page, cursor, more = changed.size > page.size, topic = record.topic))
    }

    private fun findMessage(id: String): Message? =
        topics.values.firstNotNullOfOrNull { r ->
            r.messages.firstOrNull {
                it.id ==
                    id
            }
        }

    // §6 The growing message.

    private fun composerOf(
        record: TopicRecord,
        person: Person,
    ) = composers.getOrPut(record.topic.id to requireNotNull(person.id)) { ComposerRecord() }

    private fun composerState(
        record: TopicRecord,
        person: Person,
    ): ComposerState {
        val composer = composerOf(record, person)
        val held = composer.heldUntil?.takeIf { it > clock.now }
        return ComposerState(
            openMessageId = composer.openMessageId,
            mayStartNewAt = held,
            heldUntil = held,
            typingSince = composer.typingSince,
            windowSeconds = settings.windowSeconds,
            unansweredSeconds = settings.unansweredSeconds,
            replyAllowedEarly = settings.earlyReply,
            mayPost = true,
            now = clock.now,
        )
    }

    private fun typing(
        record: TopicRecord,
        person: Person,
    ): ComposerState {
        val composer = composerOf(record, person)
        if (composer.openMessageId == null &&
            composer.typingSince == null
        ) {
            composer.typingSince = clock.now
        }
        return composerState(record, person)
    }

    private fun commit(
        record: TopicRecord,
        person: Person,
        request: RecordedRequest,
    ): MockResponse {
        val commit =
            try {
                IdejoJson.decodeFromString<Commit>(request.body?.utf8().orEmpty())
            } catch (_: kotlinx.serialization.SerializationException) {
                return error(HTTP_BAD_REQUEST, "invalid_request", "That is not a commit.")
            }
        val composer = composerOf(record, person)
        val held = composer.heldUntil?.takeIf { it > clock.now }
        val open = composer.openMessageId
        val text = punctuate(commit.bodyMd)

        if (commit.new && open != null) {
            // A reply while a message is open: it closes, and the reply starts at once if
            // early replies are allowed, else not before the timers run out.
            if (!settings.earlyReply && withinWindow(composer)) return tooSoon()
            close(record, open, held = false)
            composer.openMessageId = null
        } else if (open == null && held != null) {
            return tooSoon()
        }

        val message =
            if (composer.openMessageId == null) {
                if (text.isBlank()) return ok(CommitResult(null, composerState(record, person)))
                openMessage(record, person, text, commit.replyTo).also {
                    composer.openMessageId = it.id
                    composer.lastOpenedAt = it.openedAt
                    composer.typingSince = null
                }
            } else {
                append(record, requireNotNull(composer.openMessageId), text)
            }

        val result =
            if (commit.breakMessage) {
                val early = withinWindow(composer)
                composer.openMessageId = null
                if (early) {
                    composer.heldUntil =
                        composer.lastOpenedAt?.plusSeconds(settings.windowSeconds.toLong())
                }
                close(record, message.id, held = early)
            } else {
                message
            }
        return ok(CommitResult(result, composerState(record, person)))
    }

    private fun withinWindow(composer: ComposerRecord): Boolean =
        composer.lastOpenedAt?.let {
            Duration.between(it, clock.now).seconds <
                settings.windowSeconds
        }
            ?: false

    private fun tooSoon() =
        error(
            HTTP_CONFLICT,
            ApiError.TOO_SOON,
            "You cannot post a new message yet. Append your old message.",
        )

    /** The server adds a full stop after unpunctuated text (API guide §6). */
    private fun punctuate(text: String): String {
        val trimmed = text.trimEnd()
        if (!settings.autoPunctuation || trimmed.isEmpty()) return trimmed
        return if (trimmed.last().isLetterOrDigit()) "$trimmed." else trimmed
    }

    private fun openMessage(
        record: TopicRecord,
        person: Person,
        text: String,
        replyTo: List<String> = emptyList(),
    ): Message {
        val message =
            Message(
                id = "m_" + nextId++,
                topicId = record.topic.id,
                author = person,
                state = MessageState.Open,
                bodyMd = text,
                cursor = ++record.changeSeq,
                openedAt = clock.now,
                updatedAt = clock.now,
                repliesTo =
                    replyTo.mapNotNull { id ->
                        record.messages.firstOrNull { it.id == id }?.let {
                            org.idejo.app.core.model.ReplyTo(
                                it.id,
                                it.author.displayName,
                                it.bodyMd,
                            )
                        }
                    },
            )
        record.messages += message
        record.topic =
            record.topic.copy(messagesCount = record.messages.size, lastActivityAt = clock.now)
        broadcast(record, "message.opened", message)
        return message
    }

    private fun append(
        record: TopicRecord,
        id: String,
        text: String,
    ): Message = replace(record, id, "message.appended") { it.copy(bodyMd = text) }

    private fun close(
        record: TopicRecord,
        id: String,
        held: Boolean,
    ): Message =
        replace(record, id, "message.closed") {
            it.copy(state = MessageState.Closed, held = held, closedAt = clock.now)
        }

    private fun replace(
        record: TopicRecord,
        id: String,
        event: String,
        change: (Message) -> Message,
    ): Message {
        val index = record.messages.indexOfFirst { it.id == id }
        val message =
            change(
                record.messages[index],
            ).copy(cursor = ++record.changeSeq, updatedAt = clock.now)
        record.messages[index] = message
        broadcast(record, event, message)
        return message
    }

    private fun broadcast(
        record: TopicRecord,
        event: String,
        message: Message,
    ) {
        val payload =
            JsonObject(
                mapOf(
                    "message" to output.encodeToJsonElement(Message.serializer(), message),
                    "cursor" to json(message.cursor),
                ),
            )
        sockets.toList().forEach { it.push("topic:" + record.topic.id, event, payload) }
    }

    // §7 The socket.

    private fun socketUpgrade(request: RecordedRequest): MockResponse {
        if (apiOff) return status(HTTP_NOT_FOUND)
        val token =
            request.headers["Sec-WebSocket-Protocol"]
                ?.split(',')
                ?.map { it.trim() }
                ?.firstOrNull { it.startsWith(AUTH_TOKEN_PREFIX) }
                ?.removePrefix(AUTH_TOKEN_PREFIX)
                // As Phoenix's Base.decode64!(padding: false): the standard alphabet, unpadded.
                ?.takeIf { encoded -> encoded.none { it == '-' || it == '_' || it == '=' } }
                ?.decodeBase64()
                ?.utf8()
        if (token == null) return status(HTTP_FORBIDDEN)
        tokens[token] ?: return status(HTTP_FORBIDDEN)
        val socket = FakeSocket(this, token)
        sockets += socket
        return MockResponse
            .Builder()
            .addHeader("Sec-WebSocket-Protocol", "phoenix")
            .webSocketUpgrade(socket.listener)
            .build()
    }

    /** A topic channel's join: the topic must exist and be visible. */
    internal fun joinTopic(id: String): JsonObject? =
        synchronized(lock) {
            topics[id]?.let { record ->
                if (categories.any { it.id == record.topic.categoryId && it.permissions.view }) {
                    buildJsonObject { put("cursor", record.changeSeq) }
                } else {
                    null
                }
            }
        }

    internal fun forget(socket: FakeSocket) = synchronized(lock) { sockets.remove(socket) }

    // Answers.

    private inline fun <reified T> ok(value: T): MockResponse =
        json(output.encodeToString(value), HTTP_OK)

    private fun json(
        body: String,
        status: Int,
    ) = MockResponse
        .Builder()
        .code(status)
        .addHeader("Content-Type", "application/json; charset=utf-8")
        .body(body)
        .build()

    private fun status(status: Int) = MockResponse.Builder().code(status).build()

    private fun error(
        status: Int,
        code: String,
        message: String,
    ) = json(output.encodeToString(ErrorBody(ApiError(code, message))), status)

    private fun notFound() = error(HTTP_NOT_FOUND, ApiError.NOT_FOUND, "Not found.")

    private fun unauthenticated() =
        error(HTTP_UNAUTHORIZED, ApiError.UNAUTHENTICATED, "Sign in first.")

    private fun form(request: RecordedRequest): Map<String, String> =
        request.body?.utf8().orEmpty().split('&').filter { it.contains('=') }.associate {
            decode(it.substringBefore('=')) to decode(it.substringAfter('='))
        }

    private fun randomId(): String =
        ByteArray(ID_BYTES).also(random::nextBytes).let {
            it.toByteString().hex()
        }

    private fun encodeCursor(offset: Int) = "o$offset".encodeUtf8().base64Url()

    private fun decodeCursor(cursor: String) =
        cursor
            .decodeBase64()
            ?.utf8()
            ?.removePrefix("o")
            ?.toIntOrNull()

    private class PendingCode(
        val person: Person,
        val challenge: String,
        val at: Instant,
        var used: Boolean = false,
        var token: String? = null,
    )

    private companion object {
        const val API = "/api/v1"
        const val AUTH_TOKEN_PREFIX = "base64url.bearer.phx."
        const val CHALLENGE_LENGTH = 43
        val CODE_LIFETIME: Duration = Duration.ofSeconds(60)
        const val DEFAULT_PAGE = 50
        const val MAX_PAGE = 100
        const val PREVIEW_LATEST = 3
        const val ID_BYTES = 8
        const val HTTP_OK = 200
        const val HTTP_NO_CONTENT = 204
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409
        const val HTTP_GONE = 410

        fun encode(text: String): String = java.net.URLEncoder.encode(text, StandardCharsets.UTF_8)

        fun decode(text: String): String = URLDecoder.decode(text, StandardCharsets.UTF_8)

        fun json(value: Long) = kotlinx.serialization.json.JsonPrimitive(value)
    }
}

/** The fake's answers: every field written, nulls included, as idejo's are. */
internal val output: Json =
    Json(IdejoJson) {
        encodeDefaults = true
        explicitNulls = true
    }
