// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.testing

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
import org.idejo.app.core.model.Conversation
import org.idejo.app.core.model.ConversationLast
import org.idejo.app.core.model.ConversationStart
import org.idejo.app.core.model.Discovery
import org.idejo.app.core.model.Emoji
import org.idejo.app.core.model.ErrorBody
import org.idejo.app.core.model.ForwardTarget
import org.idejo.app.core.model.GifConfig
import org.idejo.app.core.model.GifSettings
import org.idejo.app.core.model.IntroClose
import org.idejo.app.core.model.LatestMessage
import org.idejo.app.core.model.ListBody
import org.idejo.app.core.model.Me
import org.idejo.app.core.model.MePatch
import org.idejo.app.core.model.MentionState
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.MessageEdit
import org.idejo.app.core.model.MessageRef
import org.idejo.app.core.model.MessageState
import org.idejo.app.core.model.MessageWindow
import org.idejo.app.core.model.NotificationSettings
import org.idejo.app.core.model.Page
import org.idejo.app.core.model.PasswordChange
import org.idejo.app.core.model.PendingTopic
import org.idejo.app.core.model.Person
import org.idejo.app.core.model.PluginBlock
import org.idejo.app.core.model.PluginRun
import org.idejo.app.core.model.Poll
import org.idejo.app.core.model.PollCreate
import org.idejo.app.core.model.PollOption
import org.idejo.app.core.model.PushRegistration
import org.idejo.app.core.model.PushTest
import org.idejo.app.core.model.Reaction
import org.idejo.app.core.model.Reactor
import org.idejo.app.core.model.ReadPosition
import org.idejo.app.core.model.ReportBody
import org.idejo.app.core.model.RoleCard
import org.idejo.app.core.model.ScheduleCreate
import org.idejo.app.core.model.Scheduled
import org.idejo.app.core.model.SearchHit
import org.idejo.app.core.model.Server
import org.idejo.app.core.model.ServerSettings
import org.idejo.app.core.model.Spoiler
import org.idejo.app.core.model.SpoilerSet
import org.idejo.app.core.model.Suggestion
import org.idejo.app.core.model.Tag
import org.idejo.app.core.model.Token
import org.idejo.app.core.model.TokenGrant
import org.idejo.app.core.model.Topic
import org.idejo.app.core.model.TopicCreate
import org.idejo.app.core.model.TopicCreated
import org.idejo.app.core.model.TopicIcon
import org.idejo.app.core.model.TopicPreview
import org.idejo.app.core.model.UnifiedPushSubscription
import org.idejo.app.core.model.Unread
import org.idejo.app.core.model.UnreadCounter
import org.idejo.app.core.model.UnreadMark
import org.idejo.app.core.model.Upload
import org.idejo.app.core.model.UserCard
import org.idejo.app.core.model.Vote
import org.idejo.app.core.model.Warning
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

    /**
     * `GET /me/communities` (API guide §9): the group's forums the member joined. Asked of
     * an inbox (idejo ID mode, SI-06), every one of them.
     */
    @Volatile var communities: List<org.idejo.app.core.model.Community> = emptyList()

    /**
     * The group's directory (SI-08), served at `/directory/`: when set, discovery names it as
     * `community.directory_url`, as an inbox's does.
     */
    @Volatile var directory: List<org.idejo.app.core.model.DirectoryForum>? = null

    /** False plays a server older than idejo 0.3.16: no `GET /me/followed` (IX-02). */
    @Volatile var followedList: Boolean = true

    /**
     * False plays a server before callisto-rpg/Idejo#372 (IX-15): `GET /me` has no
     * `graphical_composer`, `controls_on_mobile` or `controls_on_desktop`.
     */
    @Volatile var memberChoices: Boolean = true

    /** False plays a forum whose push is off (`GET /server`'s `push`). */
    @Volatile var pushEnabled: Boolean = true

    /** `GET /server`'s `push_vapid_key` (IX-16); null plays an idejo without it. */
    @Volatile var pushVapidKey: String? = FAKE_VAPID_KEY

    /** What each `POST /me/push/unifiedpush` sent, by registration id (NT-02). */
    val pushSubscriptions: MutableMap<String, UnifiedPushSubscription> =
        java.util.concurrent.ConcurrentHashMap()

    /** False plays a forum that signs in only with its provider (idejo AC-09): no passwords. */
    @Volatile var passwords: Boolean = true

    /** Passwords changed with `PUT /me/password`, by person; [FakePeople.PASSWORD] before. */
    private val passwordOf = mutableMapOf<String, String>()

    /** False plays a forum whose discovery gives no `account.deletion_url` (ST-06). */
    @Volatile var accountDeletion: Boolean = true

    private val categories = seedCategories().toMutableList()
    private val topics = linkedMapOf("t_1" to seedTopic(clock.now))
    private val reactors = mutableMapOf<String, MutableList<Pair<Person, List<String>>>>()
    private val reports = mutableListOf<Triple<String, String, String>>()
    private val readers = mutableMapOf<String, Reader>()

    /** Each poll's votes: poll id, then person id, then their answers (TV-27). */
    private val votes = mutableMapOf<String, MutableMap<String, List<String>>>()
    private val scheduled = mutableListOf<Pair<String, Scheduled>>()
    private val composers = mutableMapOf<Pair<String, String>, ComposerRecord>()
    private val tokens = mutableMapOf<String, Person>()

    /** The devices signed in, by id, as **Apps and devices** lists them (ST-05). */
    private val devices = mutableMapOf<String, DeviceRecord>()

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

    /** A token for [person], as if they had signed in on [name]. */
    fun tokenFor(
        person: Person,
        name: String = "Android device",
    ): AccessToken = synchronized(lock) { newToken(person, name) }

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
                PendingCode(
                    person,
                    requireNotNull(url.queryParameter("code_challenge")),
                    clock.now,
                    deviceName = url.queryParameter("device_name"),
                )
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
                openMessage(record, person, punctuate(person, text)).let {
                    close(record, it.id, held = false)
                }
            news(record, person)
            message
        }

    /** [poll] put under message [messageId], as its author's `!poll` would (TV-27). */
    fun attachPoll(
        messageId: String,
        poll: Poll,
    ) = synchronized(lock) {
        val record = topics.values.first { r -> r.messages.any { it.id == messageId } }
        val index = record.messages.indexOfFirst { it.id == messageId }
        record.messages[index] = record.messages[index].copy(poll = poll)
    }

    /** Topic [topicId] was started from message [messageId] (idejo TP-09). */
    fun continueFrom(
        topicId: String,
        messageId: String,
    ) = synchronized(lock) {
        val record = requireNotNull(topics[topicId])
        record.topic = record.topic.copy(continuesFrom = MessageRef(messageId))
    }

    /** Message [messageId] deleted by a moderator: it keeps its place, without its text. */
    fun deleteByModerator(messageId: String) =
        synchronized(lock) {
            val record = topics.values.first { r -> r.messages.any { it.id == messageId } }
            val index = record.messages.indexOfFirst { it.id == messageId }
            record.messages[index] =
                record.messages[index].copy(
                    state = MessageState.Deleted,
                    deletedBy = "moderator",
                    bodyMd = null,
                )
        }

    /** What [person] chose in poll [pollId]. */
    fun votesOf(
        person: Person,
        pollId: String,
    ): List<String> = synchronized(lock) { votes[pollId]?.get(person.id).orEmpty() }

    /** A category added to the forum, as an administrator would. */
    fun addCategory(category: Category) = synchronized(lock) { categories += category }

    /** [person]'s "Members can send me private messages" (PC-03). */
    fun acceptPrivateMessages(
        person: Person,
        accepts: Boolean,
    ) = synchronized(lock) { reader(person).acceptsPrivate = accepts }

    /** The private conversation between [a] and [b], found or started (PC-01). */
    fun conversation(
        a: Person,
        b: Person,
    ): String =
        synchronized(lock) {
            val pair = setOfNotNull(a.id, b.id)
            topics.values
                .firstOrNull { it.participants == pair }
                ?.topic
                ?.id
                ?: run {
                    val id = "p_" + nextId++
                    val topic =
                        Topic(
                            id = id,
                            title = "Private conversation",
                            url = url.resolve("/p/$id")?.toString(),
                            author = a,
                            private = true,
                            createdAt = clock.now,
                            lastActivityAt = clock.now,
                        )
                    topics[id] = TopicRecord(topic, participants = pair)
                    id
                }
        }

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

    /** [person] new here: the welcome card not seen yet, nor Getting started. */
    fun newMember(person: Person) =
        synchronized(lock) {
            reader(person).welcomed = false
            reader(person).introDue = false
        }

    /** [person] has seen the welcome card. */
    fun welcomed(person: Person) = synchronized(lock) { reader(person).welcomed = true }

    /** [person] signed in on the web, as the app's Custom Tab does: Getting started is due. */
    fun signedInOnWeb(person: Person) =
        synchronized(lock) {
            val reader = reader(person)
            if (!reader.introHidden) reader.introDue = true
        }

    /**
     * A live notification for [person] (NT-01), as idejo's user:me sends it: `mention`,
     * `reply`, `role_mention`, `follow` or `reaction` (with [emojiUrl]), by Mina in
     * "Tomatoes".
     */
    fun notify(
        person: Person,
        kind: String,
        topicId: String,
        messageId: String = "m_live",
        emojiUrl: String? = null,
    ) = synchronized(lock) {
        val payload =
            buildJsonObject {
                put("kind", kind)
                put("message_id", messageId)
                put("topic_id", topicId)
                put("topic_title", "Tomatoes")
                put("author_name", "Mina")
                put("excerpt", "Sungold every year")
                put("emoji_url", emojiUrl)
            }
        socketsOf(person).forEach { it.push("user:me", "notification", payload) }
    }

    /**
     * A moderator warns [person] about [messageId] (TV-11): waiting until acknowledged, and
     * the `warning` notification on their user:me channels, as idejo's.
     */
    fun warn(
        person: Person,
        reason: String,
        messageId: String? = null,
    ): Warning =
        synchronized(lock) {
            val warning =
                Warning(
                    id = "w_" + nextId++,
                    reason = reason,
                    messageId = messageId,
                    createdAt = clock.now,
                )
            reader(person).warnings += warning
            val payload =
                buildJsonObject {
                    put("kind", "warning")
                    put("message_id", messageId)
                    put("reason", reason)
                    put("warning_id", warning.id)
                }
            socketsOf(person).forEach { it.push("user:me", "notification", payload) }
            warning
        }

    /** The warnings [person] has not acknowledged. */
    fun warningsOf(person: Person): List<Warning> =
        synchronized(lock) { reader(person).warnings.toList() }

    /**
     * A browser or device of [person] taking push, as a registration would make it (NT-02),
     * made by [token] when given: `this_device` is that one.
     */
    fun pushRegistration(
        person: Person,
        name: String,
        token: AccessToken? = null,
    ): PushRegistration =
        synchronized(lock) {
            val registration =
                PushRegistration(
                    id = "p_" + nextId++,
                    kind = "unifiedpush",
                    name = name,
                    endpointHost = "ntfy.example.net",
                    createdAt = clock.now,
                )
            reader(person).push[registration.id] = PushRecord(registration, token?.value)
            registration
        }

    /** The self-select roles [person] holds, by id. */
    fun rolesOf(person: Person): Set<String> = synchronized(lock) { reader(person).roles.toSet() }

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

    /** Every socket of the app cut, as a network change would: the app connects again. */
    fun dropSockets() = synchronized(lock) { sockets.toList() }.forEach { it.close() }

    /** A topic's messages as the server has them. */
    fun messages(topicId: String): List<Message> =
        synchronized(lock) { topics.getValue(topicId).messages.toList() }

    // Routing.

    private fun route(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        if (path == "/.well-known/idejo") return discovery()
        if (path == "$DIRECTORY/api/v1/forums") return directoryPage(request)
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
            Route("PUT", "me/password") { call -> changePassword(call) },
            Route("PUT", "me/avatar") { call -> picture(call, dark = false) },
            Route("PUT", "me/avatar-dark") { call -> picture(call, dark = true) },
            Route("DELETE", "me/avatar") { call ->
                member(call) { it.avatarUrl = null }
            },
            Route("DELETE", "me/avatar-dark") { call ->
                member(call) { it.avatarDarkUrl = null }
            },
            Route("GET", "me/tokens") { call ->
                val person = call.viewer ?: return@Route unauthenticated()
                val current = call.request.headers["Authorization"]?.removePrefix("Bearer ")
                ok(
                    ListBody(
                        devices
                            .filterValues { tokens[it.token]?.id == person.id }
                            .map { (id, device) ->
                                Token(
                                    id = id,
                                    name = device.name,
                                    scopes = IdejoClient.SCOPES,
                                    signedInAt = device.signedInAt,
                                    current = device.token == current,
                                )
                            },
                    ),
                )
            },
            Route("DELETE", "me/tokens/{id}") { call ->
                val person = call.viewer ?: return@Route unauthenticated()
                val device = devices[call["id"]]
                if (device == null || tokens[device.token]?.id != person.id) {
                    notFound()
                } else {
                    revokeLocked(device.token)
                    status(HTTP_NO_CONTENT)
                }
            },
            Route("GET", "me/notification-settings") { call ->
                call.viewer?.let { ok(notificationSettings(it)) } ?: unauthenticated()
            },
            Route("PATCH", "me/notification-settings") { call ->
                val person = call.viewer ?: return@Route unauthenticated()
                val change =
                    IdejoJson.decodeFromString<NotificationSettings>(
                        call.request.body?.utf8() ?: "{}",
                    )
                ok(saveNotificationSettings(person, change))
            },
            Route("GET", "me/ignored") { call ->
                val person = call.viewer ?: return@Route unauthenticated()
                ok(ListBody(FakePeople.all.filter { it.id in reader(person).ignored }))
            },
            Route("GET", "me/push") { call ->
                val person = call.viewer ?: return@Route unauthenticated()
                val current = call.request.headers["Authorization"]?.removePrefix("Bearer ")
                ok(
                    ListBody(
                        reader(person).push.values.map { record ->
                            record.registration.copy(
                                thisDevice = record.token != null && record.token == current,
                            )
                        },
                    ),
                )
            },
            Route("POST", "me/push/unifiedpush") { call -> registerPush(call) },
            Route("DELETE", "me/push/unifiedpush/{id}") { call ->
                val person = call.viewer ?: return@Route unauthenticated()
                if (reader(person).push.remove(call["id"]) == null) {
                    notFound()
                } else {
                    status(HTTP_NO_CONTENT)
                }
            },
            Route("POST", "me/push/test") { call ->
                val person = call.viewer ?: return@Route unauthenticated()
                ok(PushTest(reader(person).push.size))
            },
            Route("POST", "me/welcome") { call ->
                member(call) { it.welcomed = true }
            },
            Route("POST", "me/intro") { call ->
                val hide =
                    IdejoJson
                        .decodeFromString<IntroClose>(call.request.body?.utf8() ?: "{}")
                        .hide
                member(call) { reader ->
                    reader.introDue = false
                    if (hide) reader.introHidden = true
                }
            },
            Route("GET", "users/{id}") { call -> card(call.viewer, call["id"]) },
            Route("GET", "roles/{id}") { call -> roleCard(call["id"]) },
            Route("PUT", "users/{id}/ignore") { call ->
                val viewer = call.viewer
                if (viewer != null && viewer.id != call["id"]) reader(viewer).ignored += call["id"]
                card(viewer, call["id"])
            },
            Route("DELETE", "users/{id}/ignore") { call ->
                call.viewer?.let { reader(it).ignored -= call["id"] }
                card(call.viewer, call["id"])
            },
            Route("PUT", "me/roles/{id}") { call ->
                member(call) { reader ->
                    if (settings.roles.any { it.id == call["id"] }) reader.roles += call["id"]
                }
            },
            Route("DELETE", "me/roles/{id}") { call ->
                member(call) { it.roles -= call["id"] }
            },
            Route("PATCH", "me") { call ->
                val body = call.request.body?.utf8() ?: "{}"
                // Only the fields sent change (API guide: `PATCH /me`); unknown ones ignored.
                val patch = IdejoJson.decodeFromString<MePatch>(body)
                // idejo's own check (AC-04): a display name is never blank.
                if (patch.displayName?.isBlank() == true) {
                    return@Route error(
                        HTTP_UNPROCESSABLE_ENTITY,
                        "validation",
                        "Display name can't be blank.",
                    )
                }
                member(call) { reader ->
                    patch.displayName?.let { reader.displayName = it.trim() }
                    patch.userTag?.let {
                        reader.userTag = it.trim().ifEmpty { null }
                        reader.userTagChanged = true
                    }
                    patch.bioMd?.let { reader.bioMd = it.ifBlank { null } }
                    patch.autoPunctuation?.let { reader.autoPunctuation = it }
                    patch.graphicalComposer?.let { reader.graphicalComposer = it }
                    patch.controlsOnMobile?.let { reader.controlsOnMobile = it }
                    patch.controlsOnDesktop?.let { reader.controlsOnDesktop = it }
                    patch.acceptsPrivateMessages?.let { reader.acceptsPrivate = it }
                }
            },
            Route("GET", "conversations") { call ->
                call.viewer?.let { ok(ListBody(conversationsOf(it))) } ?: unauthenticated()
            },
            Route("POST", "conversations") { call -> startConversation(call) },
            Route("GET", "conversations/{id}") { call ->
                call.viewer
                    ?.let { viewer -> conversationsOf(viewer).firstOrNull { it.id == call["id"] } }
                    ?.let { ok(it) }
                    ?: notFound()
            },
            Route("GET", "me/communities") { ok(ListBody(communities)) },
            Route("GET", "me/warnings") { call ->
                call.viewer?.let { ok(ListBody(reader(it).warnings.toList())) } ?: unauthenticated()
            },
            Route("POST", "me/warnings/acknowledge") { call ->
                call.viewer?.let {
                    reader(it).warnings.clear()
                    status(HTTP_NO_CONTENT)
                } ?: unauthenticated()
            },
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
            Route("GET", "me/followed") { call ->
                val viewer = call.viewer
                when {
                    !followedList -> {
                        notFound()
                    }

                    viewer == null -> {
                        unauthenticated()
                    }

                    else -> {
                        val followed = reader(viewer).followed.toList()
                        val all = followed.mapNotNull { topics[it]?.topic?.seenBy(viewer) }
                        ok(paged(all, call.request))
                    }
                }
            },
            Route("GET", "unread") { call ->
                call.viewer?.let { ok(unreadOf(it)) }
                    ?: unauthenticated()
            },
            Route("GET", "topics/{id}") { call ->
                topics[call["id"]]?.takeIf { readable(it, call.viewer) }?.let { record ->
                    ok(
                        record.topic
                            .seenBy(call.viewer)
                            .copy(
                                firstMessageId = record.messages.firstOrNull()?.id,
                                myLastMessageId =
                                    record.messages
                                        .lastOrNull {
                                            it.author.id == call.viewer?.id &&
                                                it.state != MessageState.Deleted
                                        }?.id,
                            ),
                    )
                }
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
            Route("GET", "topics/{id}/messages") { call ->
                window(call["id"], call.viewer, call.request)
            },
            Route("PUT", "topics/{id}/read") { call ->
                val upTo =
                    IdejoJson
                        .decodeFromString<ReadPosition>(call.request.body?.utf8() ?: "{}")
                        .messageId
                reading(call) { reader, id -> readTo(reader, id, upTo) }
            },
            Route("GET", "topics/{id}/changes") { call -> changes(call["id"], call.request) },
            Route("GET", "topics/{id}/pins") { call -> pins(call["id"]) },
            Route(
                "GET",
                "messages/{id}",
            ) { call -> findMessage(call["id"])?.let(::ok) ?: notFound() },
            Route("PUT", "messages/{id}") { call -> editOwn(call) },
            Route("DELETE", "messages/{id}") { call -> deleteOwn(call) },
            Route("GET", "topics/{id}/commands") { ok(ListBody(FAKE_COMMANDS)) },
            Route("GET", "topic-icons") {
                ok(ListBody(listOf(TopicIcon("i_seed", "seedling"), TopicIcon("i_sun", "sun"))))
            },
            Route("GET", "gifs") { ok(GifSettings(settings.gifs != null, settings.gifs)) },
            Route("POST", "categories/{id}/topics") { call -> startTopic(call, call["id"], null) },
            Route("POST", "messages/{id}/new-topic") { call ->
                val body =
                    IdejoJson.decodeFromString<TopicCreate>(
                        call.request.body?.utf8() ?: "{}",
                    )
                startTopic(call, body.categoryId.orEmpty(), call["id"])
            },
            Route("POST", "uploads") { call ->
                if (call.viewer == null) return@Route unauthenticated()
                val id = "up_" + nextId++
                ok(Upload(id, "/uploads/$id.png", "![image](/uploads/$id.png)", 640, 480))
            },
            Route("GET", "topics/{id}/scheduled") { call ->
                val person = call.viewer ?: return@Route unauthenticated()
                ok(
                    ListBody(
                        scheduled
                            .filter {
                                it.first == person.id &&
                                    it.second.topicId == call["id"]
                            }.map { it.second },
                    ),
                )
            },
            Route("POST", "topics/{id}/scheduled") { call ->
                val person = call.viewer ?: return@Route unauthenticated()
                val body =
                    IdejoJson.decodeFromString<ScheduleCreate>(call.request.body?.utf8() ?: "{}")
                if (body.hours <= 0 || body.hours > MAX_HOURS) {
                    return@Route error(
                        HTTP_UNPROCESSABLE_ENTITY,
                        "invalid_request",
                        "At most 168 hours.",
                    )
                }
                val item =
                    Scheduled(
                        "s_" + nextId++,
                        clock.now.plusSeconds((body.hours * SECONDS_PER_HOUR).toLong()),
                        call["id"],
                        body.bodyMd,
                    )
                scheduled.add(person.id.orEmpty() to item)
                ok(item)
            },
            Route("PUT", "messages/{id}/reactions/{emoji}") { call -> react(call, on = true) },
            Route("DELETE", "messages/{id}/reactions/{emoji}") { call -> react(call, on = false) },
            Route("GET", "messages/{id}/reactions") { call ->
                val message = findMessage(call["id"]) ?: return@Route notFound()
                ok(
                    ListBody(
                        reactors[message.id].orEmpty().map { (who, names) ->
                            Reactor(who, names.map { ":$it:" })
                        },
                    ),
                )
            },
            Route("POST", "messages/{id}/report") { call ->
                val person = call.viewer ?: return@Route unauthenticated()
                val message = findMessage(call["id"]) ?: return@Route notFound()
                val body = IdejoJson.decodeFromString<ReportBody>(call.request.body?.utf8() ?: "{}")
                if (body.reason.isBlank()) {
                    return@Route error(HTTP_UNPROCESSABLE_ENTITY, "invalid_request", "Say why.")
                }
                reports += Triple(person.id.orEmpty(), message.id, body.reason)
                status(HTTP_NO_CONTENT)
            },
            Route("DELETE", "scheduled/{id}") { call ->
                val person = call.viewer ?: return@Route unauthenticated()
                val removed =
                    scheduled.removeAll {
                        it.first == person.id &&
                            it.second.id == call["id"]
                    }
                if (removed) status(HTTP_NO_CONTENT) else notFound()
            },
            Route("POST", "topics/{id}/polls") { call ->
                val body = IdejoJson.decodeFromString<PollCreate>(call.request.body?.utf8() ?: "{}")
                val poll =
                    Poll(
                        "p_" + nextId++,
                        body.question,
                        body.multiple,
                        options =
                            body.options.mapIndexed { i, answer ->
                                PollOption("o_$i", answer.text, answer.emoji, answer.colour)
                            },
                    )
                underOrAlone(call, body.messageId, body.question) { it.copy(poll = poll) }
            },
            Route("POST", "topics/{id}/plugins/{command}") { call ->
                val body = IdejoJson.decodeFromString<PluginRun>(call.request.body?.utf8() ?: "{}")
                val block =
                    PluginBlock(
                        call["command"],
                        "button",
                        kotlinx.serialization.json.JsonObject(
                            body.params.mapValues {
                                kotlinx.serialization.json.JsonPrimitive(
                                    it.value,
                                )
                            },
                        ),
                    )
                underOrAlone(
                    call,
                    body.messageId,
                    body.params.values
                        .firstOrNull()
                        .orEmpty(),
                ) {
                    it.copy(plugins = it.plugins + block)
                }
            },
            Route("PUT", "messages/{id}/spoiler") { call ->
                val set = IdejoJson.decodeFromString<SpoilerSet>(call.request.body?.utf8() ?: "{}")
                own(call) { it.copy(spoiler = Spoiler(set.overlay)) }
            },
            Route(
                "DELETE",
                "messages/{id}/spoiler",
            ) { call -> own(call) { it.copy(spoiler = null) } },
            Route("GET", "emoji") { call ->
                val q =
                    call.request.url
                        .queryParameter("q")
                        .orEmpty()
                ok(ListBody(FAKE_EMOJI.filter { q in it.name }))
            },
            Route("GET", "suggest") { call ->
                val q =
                    call.request.url
                        .queryParameter("q")
                        .orEmpty()
                val people = listOf(FakePeople.Ada, FakePeople.Leo)
                when (call.request.url.queryParameter("sigil")) {
                    "@" -> {
                        ok(
                            ListBody(
                                people
                                    .filter {
                                        it.username.orEmpty().startsWith(
                                            q,
                                            ignoreCase = true,
                                        )
                                    }.map {
                                        Suggestion(
                                            "user",
                                            it.username.orEmpty(),
                                            it.displayName,
                                            it.id,
                                        )
                                    },
                            ),
                        )
                    }

                    null -> {
                        error(HTTP_BAD_REQUEST, "invalid_request", "No sign.")
                    }

                    else -> {
                        ok(ListBody(emptyList<Suggestion>()))
                    }
                }
            },
            Route("GET", "forward-targets") { call ->
                val except = call.request.url.queryParameter("except")
                val q =
                    call.request.url
                        .queryParameter("q")
                        .orEmpty()
                ok(
                    ListBody(
                        topics.values
                            .map { it.topic }
                            .filter { it.id != except && it.title.contains(q, ignoreCase = true) }
                            .map { ForwardTarget(it.id, it.title, "$address/t/${it.id}") },
                    ),
                )
            },
            Route("PUT", "polls/{id}/vote") { call -> vote(call) },
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
                .filterNot { it.topic.private }
                .map { it.topic.seenBy(person) }
                .filter { it.unreadCount > 0 && !it.muted }
        val (following, others) = seen.partition { it.followed }
        return Unread(following, Page(others), counterOf(reader))
    }

    /** The **Unread** button: replies and mentions counted, the strongest kind named. */
    private fun counterOf(reader: Reader): UnreadCounter {
        // Conversations never count in Unread (PC-04).
        val unread = reader.unread.filterKeys { topics[it]?.topic?.private != true }
        val strong = unread.values.filter { it.state > MentionState.New }
        val level =
            unread.values
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
        if (record.topic.private) return conversationNews(record, author)
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

    /** A topic, or a conversation [viewer] takes part in. */
    private fun readable(
        record: TopicRecord,
        viewer: Person?,
    ): Boolean = !record.topic.private || viewer?.id in record.participants

    /** `GET /conversations`: [person]'s, the latest active first. */
    private fun conversationsOf(person: Person): List<Conversation> =
        topics.values
            .filter { it.topic.private && person.id in it.participants }
            .sortedByDescending { it.messages.lastOrNull()?.openedAt ?: it.topic.createdAt }
            .map { record ->
                val last = record.messages.lastOrNull { it.state != MessageState.Deleted }
                Conversation(
                    id = record.topic.id,
                    url = record.topic.url,
                    with =
                        FakePeople.all.filter {
                            it.id in record.participants &&
                                it.id != person.id
                        },
                    unreadCount = reader(person).unread[record.topic.id]?.count ?: 0,
                    lastMessage =
                        last?.let {
                            ConversationLast(it.id, it.author.displayName, it.bodyMd, it.openedAt)
                        },
                    createdAt = record.topic.createdAt,
                    lastActivityAt = last?.openedAt ?: record.topic.createdAt,
                )
            }

    /** `POST /conversations` (PC-03): refused when the other takes no private messages. */
    private fun startConversation(call: Call): MockResponse {
        val viewer = call.viewer ?: return unauthenticated()
        val body = call.request.body?.utf8() ?: "{}"
        val userId = IdejoJson.decodeFromString<ConversationStart>(body).userId
        val other =
            FakePeople.all
                .firstOrNull { it.id == userId && it.id != viewer.id }
                ?.takeIf { reader(it).acceptsPrivate }
                ?: return error(HTTP_FORBIDDEN, "forbidden", "They take no private messages.")
        val id = conversation(viewer, other)
        return ok(conversationsOf(viewer).first { it.id == id })
    }

    /** A message in a conversation: new for the other participant, `conversation.updated`. */
    private fun conversationNews(
        record: TopicRecord,
        author: Person,
    ) {
        val payload = buildJsonObject { put("conversation_id", record.topic.id) }
        FakePeople.all
            .filter { it.id in record.participants && it.id != author.id }
            .forEach { person ->
                val reader = reader(person)
                val seen = reader.unread[record.topic.id]
                reader.unread[record.topic.id] =
                    Unseen((seen?.count ?: 0) + 1, MentionState.New)
                socketsOf(person).forEach { it.push("user:me", "conversation.updated", payload) }
            }
    }

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
        if (!readable(record, person)) return notFound()
        return handle(record, person)
    }

    /** `PUT /messages/{id}` (CO-18): the author's closed message, its whole text. */
    private fun editOwn(call: Call): MockResponse {
        val person = call.viewer ?: return unauthenticated()
        val record = topics.values.firstOrNull { r -> r.messages.any { it.id == call["id"] } }
        val message = record?.messages?.first { it.id == call["id"] } ?: return notFound()
        if (message.author.id != person.id) return error(HTTP_FORBIDDEN, "forbidden", "Not yours.")
        val edit = IdejoJson.decodeFromString<MessageEdit>(call.request.body?.utf8() ?: "{}")
        return ok(
            replace(record, message.id, "message.edited") {
                it.copy(bodyMd = edit.bodyMd, edited = it.edited || edit.bodyMd != it.bodyMd)
            },
        )
    }

    /** `POST /categories/{id}/topics` and a message's `new-topic` (CO-19). */
    private fun startTopic(
        call: Call,
        categoryId: String,
        from: String?,
    ): MockResponse {
        val person = call.viewer ?: return unauthenticated()
        val category = category(categoryId) ?: return notFound()
        val body = IdejoJson.decodeFromString<TopicCreate>(call.request.body?.utf8() ?: "{}")
        val problem =
            when {
                !category.permissions.createTopic -> return error(
                    HTTP_FORBIDDEN,
                    "forbidden",
                    "Not here.",
                )

                body.title.isBlank() -> "Give the topic a title."

                category.tags.isNotEmpty() && body.tagIds.isEmpty() -> "Choose at least one tag."

                else -> null
            }
        if (problem != null) return error(HTTP_UNPROCESSABLE_ENTITY, "invalid_request", problem)
        if (category.newTopics == "approval") {
            return ok(TopicCreated(pending = PendingTopic("pt_" + nextId++, body.title)))
        }
        val tags = category.tags.filter { it.id in body.tagIds }
        val topic =
            addTopic(body.title, categoryId, person, tags).copy(
                continuesFrom =
                    from?.let {
                        org.idejo.app.core.model
                            .MessageRef(it)
                    },
            )
        val record = topics.getValue(topic.id)
        record.topic = topic
        replace(
            record,
            record.messages.first().id,
            "message.edited",
        ) { it.copy(bodyMd = body.bodyMd) }
        return ok(TopicCreated(topic))
    }

    /** A change to the viewer's own message [call]'s `{id}` (CO-13). */
    private fun own(
        call: Call,
        change: (Message) -> Message,
    ): MockResponse {
        val person = call.viewer ?: return unauthenticated()
        val record = topics.values.firstOrNull { r -> r.messages.any { it.id == call["id"] } }
        val message = record?.messages?.first { it.id == call["id"] } ?: return notFound()
        if (message.author.id != person.id) return error(HTTP_FORBIDDEN, "forbidden", "Not yours.")
        return ok(replace(record, message.id, "message.edited", change))
    }

    /** A poll or plugin block under [messageId], or a closed message of its own (CO-13). */
    private fun underOrAlone(
        call: Call,
        messageId: String?,
        text: String,
        change: (Message) -> Message,
    ): MockResponse {
        val person = call.viewer ?: return unauthenticated()
        val record = topics[call["id"]] ?: return notFound()
        val id =
            messageId
                ?: openMessage(record, person, text).id.also { close(record, it, held = false) }
        if (record.messages.none { it.id == id }) return notFound()
        return ok(replace(record, id, "message.edited", change))
    }

    /** `DELETE /messages/{id}` (CO-18): the author's own, never a topic's first. */
    private fun deleteOwn(call: Call): MockResponse {
        val person = call.viewer ?: return unauthenticated()
        val record = topics.values.firstOrNull { r -> r.messages.any { it.id == call["id"] } }
        val message = record?.messages?.first { it.id == call["id"] } ?: return notFound()
        if (message.author.id != person.id || record.messages.first().id == message.id) {
            return error(HTTP_FORBIDDEN, "forbidden", "You cannot delete this message.")
        }
        return ok(
            replace(record, message.id, "message.deleted") {
                it.copy(state = MessageState.Deleted, deletedBy = "author")
            },
        )
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
                account =
                    org.idejo.app.core.model.AccountLinks(
                        deletionUrl =
                            url
                                .resolve("/users/settings?tab=account#delete-heading")
                                ?.toString()
                                ?.takeIf { accountDeletion },
                    ),
                features = listOf("unifiedpush", "polls", "private_messages"),
                community =
                    directory?.let {
                        org.idejo.app.core.model.CommunityInfo(
                            url.resolve("$DIRECTORY/").toString(),
                        )
                    },
            ),
        )
    }

    /** The group's directory (SI-08): 20 a page, searched in names and hosts. */
    private fun directoryPage(request: RecordedRequest): MockResponse {
        val all = directory ?: return status(HTTP_NOT_FOUND)
        val query =
            request.url
                .queryParameter("q")
                .orEmpty()
                .trim()
        val page =
            request.url
                .queryParameter("page")
                ?.toIntOrNull()
                ?.coerceAtLeast(1) ?: 1
        val found =
            all.filter {
                query.isEmpty() ||
                    it.name.contains(query, ignoreCase = true) ||
                    it.host.contains(query, ignoreCase = true)
            }
        val pages = maxOf(1, (found.size + DIRECTORY_PAGE - 1) / DIRECTORY_PAGE)
        return ok(
            org.idejo.app.core.model.DirectoryPage(
                found.drop((page - 1) * DIRECTORY_PAGE).take(DIRECTORY_PAGE),
                page,
                pages,
                found.size,
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
        val token = newToken(pending.person, pending.deviceName ?: "Android device")
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

    private fun newToken(
        person: Person,
        name: String = "Android device",
    ): AccessToken =
        AccessToken("idj_" + randomId()).also { token ->
            tokens[token.value] = person
            devices["d_" + randomId()] = DeviceRecord(token.value, name, clock.now)
        }

    private fun revokeLocked(token: String) {
        if (tokens.remove(token) == null) return
        devices.values.removeAll { it.token == token }
        sockets.filter { it.token == token }.forEach { it.revoked() }
    }

    // Reading.

    private fun server(): Server =
        Server(
            name = settings.name,
            url = url.toString(),
            theme = settings.theme,
            aboutMd = settings.about,
            rulesMd = settings.rules,
            roles = settings.roles,
            commands = settings.commands,
            settings =
                ServerSettings(
                    windowSeconds = settings.windowSeconds,
                    unansweredSeconds = settings.unansweredSeconds,
                    earlyReply = settings.earlyReply,
                    push = pushEnabled,
                    pushVapidKey = pushVapidKey.takeIf { pushEnabled },
                    uploadTypes = listOf("jpg", "jpeg", "png", "gif", "webp"),
                    uploadMaxBytes = FAKE_UPLOAD_MAX_BYTES,
                ),
        )

    private fun me(person: Person): Me {
        val reader = reader(person)
        return Me(
            id = requireNotNull(person.id),
            displayName = reader.displayName ?: person.displayName,
            username = person.username,
            userTag = if (reader.userTagChanged) reader.userTag else person.userTag,
            bioMd = reader.bioMd,
            avatarUrl = reader.avatarUrl,
            avatarDarkUrl = reader.avatarDarkUrl,
            verified = true,
            roles = settings.roles.filter { it.id in reader.roles },
            welcomed = reader.welcomed,
            introDue = reader.introDue && reader.welcomed && !reader.introHidden,
            warningPending = reader.warnings.isNotEmpty(),
            acceptsPrivateMessages = reader.acceptsPrivate,
            autoPunctuation = reader.autoPunctuation,
            graphicalComposer = reader.graphicalComposer.takeIf { memberChoices },
            controlsOnMobile = reader.controlsOnMobile.takeIf { memberChoices },
            controlsOnDesktop = reader.controlsOnDesktop.takeIf { memberChoices },
        )
    }

    /** The pictures sent with `PUT /me/avatar(-dark)`, by size of the request (ST-01). */
    val pictures: MutableList<Int> = mutableListOf()

    /** [person]'s email summary and push choices (ST-03, NT-08), read back. */
    fun notificationSettingsOf(person: Person): NotificationSettings =
        synchronized(lock) { notificationSettings(person) }

    /** [person]'s choices as Profile and account sets them (ST-02, MA-01), read back. */
    fun choicesOf(person: Person): MePatch =
        synchronized(lock) {
            reader(person).let {
                MePatch(
                    autoPunctuation = it.autoPunctuation,
                    graphicalComposer = it.graphicalComposer,
                    controlsOnMobile = it.controlsOnMobile,
                    controlsOnDesktop = it.controlsOnDesktop,
                    acceptsPrivateMessages = it.acceptsPrivate,
                )
            }
        }

    /** The member's notification choices (ST-03, NT-08): idejo's defaults until changed. */
    private fun notificationSettings(person: Person): NotificationSettings =
        reader(person).notifications
            ?: NotificationSettings(
                email =
                    NotificationSettings.Email(
                        mentions = true,
                        roleMentions = false,
                        ownTopics = true,
                        followedTopics = false,
                        newTopics = false,
                        allActivity = false,
                        onceAWeek = true,
                    ),
                push =
                    NotificationSettings.Push(
                        reply = true,
                        mention = true,
                        roleMention = true,
                        followed = true,
                        privateMessages = true,
                    ),
            )

    /** Saves only the choices [change] carries (ST-03): the rest stay as they were. */
    private fun saveNotificationSettings(
        person: Person,
        change: NotificationSettings,
    ): NotificationSettings {
        val current = notificationSettings(person)
        val saved =
            current.copy(
                email = current.email?.merged(change.email),
                push = current.push?.merged(change.push),
            )
        reader(person).notifications = saved
        return saved
    }

    private fun NotificationSettings.Email.merged(
        change: NotificationSettings.Email?,
    ): NotificationSettings.Email =
        copy(
            mentions = change?.mentions ?: mentions,
            roleMentions = change?.roleMentions ?: roleMentions,
            ownTopics = change?.ownTopics ?: ownTopics,
            followedTopics = change?.followedTopics ?: followedTopics,
            newTopics = change?.newTopics ?: newTopics,
            allActivity = change?.allActivity ?: allActivity,
            onceAWeek = change?.onceAWeek ?: onceAWeek,
        )

    private fun NotificationSettings.Push.merged(
        change: NotificationSettings.Push?,
    ): NotificationSettings.Push =
        copy(
            reply = change?.reply ?: reply,
            mention = change?.mention ?: mention,
            roleMention = change?.roleMention ?: roleMention,
            followed = change?.followed ?: followed,
            privateMessages = change?.privateMessages ?: privateMessages,
        )

    /** `GET /roles/{slug}`: a self-select role and the people holding it (TV-22). */
    private fun roleCard(slug: String): MockResponse {
        val role = settings.roles.firstOrNull { it.slug == slug } ?: return notFound()
        val members = FakePeople.all.filter { role.id in reader(it).roles }
        return ok(RoleCard(role.id, role.name, role.slug, role.colour, members, members.size))
    }

    /** `GET /users/{id}`: [viewer] may ignore anyone but themselves (AC-06). */
    private fun card(
        viewer: Person?,
        id: String,
    ): MockResponse {
        val person = FakePeople.all.firstOrNull { it.id == id } ?: return notFound()
        return ok(
            UserCard(
                id = id,
                displayName = person.displayName,
                username = person.username,
                userTag = person.userTag,
                colour = person.colour,
                bioMd = "I grow *tomatoes* and ||secret|| peppers.",
                ignoredByMe = viewer?.takeIf { it.id != id }?.let { id in reader(it).ignored },
                mayMessage = viewer != null && viewer.id != id && reader(person).acceptsPrivate,
            ),
        )
    }

    /**
     * `POST /me/push/unifiedpush` (NT-02) as idejo keeps it: one registration per endpoint,
     * tied to the token that made it; https endpoints only. Kept in [pushSubscriptions].
     */
    private fun registerPush(call: Call): MockResponse {
        val person = call.viewer ?: return unauthenticated()
        if (!pushEnabled) return error(HTTP_UNPROCESSABLE_ENTITY, "push_disabled", "Push is off.")
        val body = call.request.body?.utf8() ?: "{}"
        val subscription = IdejoJson.decodeFromString<UnifiedPushSubscription>(body)
        val host =
            subscription.endpoint
                .toHttpUrlOrNull()
                ?.takeIf { it.isHttps }
                ?.host
        if (host == null || subscription.p256dh.isBlank() || subscription.auth.isBlank()) {
            return error(HTTP_UNPROCESSABLE_ENTITY, "validation", "Endpoint is invalid.")
        }
        val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
        val pushes = reader(person).push
        pushes.entries.removeAll { pushSubscriptions[it.key]?.endpoint == subscription.endpoint }
        val registration =
            PushRegistration(
                id = "p_" + nextId++,
                kind = "unifiedpush",
                name = subscription.deviceName,
                endpointHost = host,
                createdAt = clock.now,
            )
        pushes[registration.id] = PushRecord(registration, token)
        pushSubscriptions[registration.id] = subscription
        return ok(registration.copy(thisDevice = true))
    }

    /**
     * `PUT /me/password` (ST-04) as idejo checks it: the current password, at least 12
     * characters, the same twice. Then every token of the account is revoked, this one too.
     */
    private fun changePassword(call: Call): MockResponse {
        val person = call.viewer ?: return unauthenticated()
        val change =
            IdejoJson.decodeFromString<PasswordChange>(call.request.body?.utf8() ?: "{}")
        val message =
            when {
                !passwords -> {
                    "Passwords are not used here: sign in with your provider."
                }

                change.currentPassword != (passwordOf[person.id] ?: FakePeople.PASSWORD) -> {
                    "That is not your current password."
                }

                change.password.length < PASSWORD_MIN -> {
                    "New password should be at least 12 characters."
                }

                change.password != change.passwordConfirmation -> {
                    "The passwords do not match."
                }

                else -> {
                    null
                }
            }
        if (message != null) return error(HTTP_UNPROCESSABLE_ENTITY, "validation", message)
        passwordOf[requireNotNull(person.id)] = change.password
        tokens
            .filterValues { it.id == person.id }
            .keys
            .toList()
            .forEach(::revokeLocked)
        return status(HTTP_NO_CONTENT)
    }

    /** `PUT /me/avatar(-dark)`: a multipart `image`, kept as a new upload (ST-01). */
    private fun picture(
        call: Call,
        dark: Boolean,
    ): MockResponse {
        val body =
            call.request.body
                ?.utf8()
                .orEmpty()
        if ("name=\"image\"" !in body) {
            return error(HTTP_BAD_REQUEST, "bad_request", "The request is not valid.")
        }
        val url = "/uploads/avatar_" + nextId++ + ".webp"
        pictures += body.length
        return member(call) { if (dark) it.avatarDarkUrl = url else it.avatarUrl = url }
    }

    /** A change to the signed-in member, answered with `GET /me`. */
    private fun member(
        call: Call,
        change: (Reader) -> Unit,
    ): MockResponse {
        val person = call.viewer ?: return unauthenticated()
        change(reader(person))
        return ok(me(person))
    }

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
                .filterNot { it.topic.private }
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

    /**
     * `GET /topics/{id}/messages` as idejo answers it: the end, `around` a message, the first
     * unread (`first_unread=true`), or a page from a cursor (`before:<id>`, `after:<id>`;
     * idejo's are opaque and signed), with `earlier` and `later` counted (IX-09).
     */
    private fun window(
        topicId: String,
        viewer: Person?,
        request: RecordedRequest,
    ): MockResponse {
        val record = topics[topicId] ?: return notFound()
        val all = record.messages.toList()
        val query = request.url
        val limit = query.queryParameter("limit")?.toIntOrNull()?.coerceIn(1, MAX_PAGE)
        val unseen = viewer?.let { reader(it).unread[topicId] }
        val firstUnread =
            unseen?.let { all.getOrNull((all.size - it.count).coerceAtLeast(0)) }
        val cursor = query.queryParameter("cursor")
        val around =
            query.queryParameter("around")
                ?: firstUnread?.id?.takeIf { query.queryParameter("first_unread") == "true" }
        val range: IntRange =
            when {
                cursor != null -> {
                    pageRange(all, cursor, limit ?: DEFAULT_PAGE)
                        ?: return error(HTTP_BAD_REQUEST, "bad_cursor", "Bad cursor.")
                }

                around != null -> {
                    aroundRange(all, around) ?: return notFound()
                }

                else -> {
                    (all.size - (limit ?: DEFAULT_PAGE)).coerceAtLeast(0)..all.lastIndex
                }
            }
        return ok(windowOf(record, range, firstUnread?.let { UnreadMark(it.id, unseen.count) }))
    }

    /** The window over [range] of [record]'s messages, cursors and counts with it. */
    private fun windowOf(
        record: TopicRecord,
        range: IntRange,
        firstUnread: UnreadMark?,
    ): MessageWindow {
        val all = record.messages.toList()
        val data = all.slice(range)
        if (data.isEmpty()) return MessageWindow(cursor = record.changeSeq)
        return MessageWindow(
            data = data,
            firstMessage = all.first(),
            firstUnread = firstUnread,
            nextCursor = data.last().takeIf { it != all.last() }?.let { "after:${it.id}" },
            prevCursor = data.first().takeIf { it != all.first() }?.let { "before:${it.id}" },
            cursor = record.changeSeq,
            total = all.size,
            earlier = (range.first - 1).coerceAtLeast(0),
            later = all.lastIndex - range.last,
        )
    }

    /** The page a cursor names: [n] messages `before:<id>` or `after:<id>`. */
    private fun pageRange(
        all: List<Message>,
        cursor: String,
        n: Int,
    ): IntRange? {
        val (side, id) = cursor.split(":", limit = 2).takeIf { it.size == 2 } ?: return null
        val at = all.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return null
        return if (side ==
            "before"
        ) {
            (at - n).coerceAtLeast(0) until at
        } else {
            at + 1..minOf(at + n, all.lastIndex)
        }
    }

    /** idejo's window around a message: [WINDOW_BEFORE] before it, [WINDOW_AFTER] after. */
    private fun aroundRange(
        all: List<Message>,
        id: String,
    ): IntRange? {
        val at = all.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return null
        return (at - WINDOW_BEFORE).coerceAtLeast(0)..minOf(at + WINDOW_AFTER, all.lastIndex)
    }

    /** `PUT /topics/{id}/read`: read up to [messageId], or the whole topic. */
    private fun readTo(
        reader: Reader,
        topicId: String,
        messageId: String?,
    ) {
        val unseen = reader.unread[topicId] ?: return
        val all = topics.getValue(topicId).messages
        val at = messageId?.let { id -> all.indexOfFirst { it.id == id } } ?: all.lastIndex
        val left = minOf(unseen.count, all.lastIndex - at)
        if (left <=
            0
        ) {
            reader.unread.remove(topicId)
        } else {
            reader.unread[topicId] = unseen.copy(count = left)
        }
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

    /** `PUT /polls/{id}/vote`: the poll as the voter now sees it; none after it closed. */
    private fun vote(call: Call): MockResponse {
        val person = call.viewer ?: return unauthenticated()
        val poll =
            topics.values
                .flatMap { it.messages }
                .firstNotNullOfOrNull { m -> m.poll?.takeIf { it.id == call["id"] } }
                ?: return notFound()
        if (poll.closed) return error(HTTP_CONFLICT, "poll_closed", "This poll is closed.")
        val chosen =
            IdejoJson.decodeFromString<Vote>(call.request.body?.utf8() ?: "{}").optionIds
        val ballots = votes.getOrPut(poll.id) { mutableMapOf() }
        val voter = requireNotNull(person.id)
        if (chosen.isEmpty()) ballots.remove(voter) else ballots[voter] = chosen
        val shown = !poll.hiddenUntilClosed
        return ok(
            poll.copy(
                myVotes = chosen,
                resultsShown = shown,
                total = if (shown) ballots.size else null,
                options =
                    poll.options.map { option ->
                        option.copy(
                            count = if (shown) ballots.values.count { option.id in it } else null,
                        )
                    },
            ),
        )
    }

    /** `PUT` and `DELETE /messages/{id}/reactions/{emoji}` (MA-03), live as on the server. */
    private fun react(
        call: Call,
        on: Boolean,
    ): MockResponse {
        val person = call.viewer ?: return unauthenticated()
        val record = topics.values.firstOrNull { r -> r.messages.any { it.id == call["id"] } }
        val message = record?.messages?.first { it.id == call["id"] } ?: return notFound()
        val name = call["emoji"].trim(':')
        val who = reactors.getOrPut(message.id) { mutableListOf() }
        val index = who.indexOfFirst { it.first.id == person.id }
        val mine = if (index >= 0) who[index].second else emptyList()
        val next = if (on) (mine + name).distinct() else mine - name
        if (index >= 0) who.removeAt(index)
        if (next.isNotEmpty()) who += person to next
        val reactions =
            who
                .flatMap { (p, names) -> names.map { it to p.id } }
                .groupBy({ it.first }, { it.second })
                .map { (n, ids) ->
                    Reaction(
                        ":$n:",
                        n,
                        ids.size,
                        char = FAKE_EMOJI_CHARS[n],
                        mine =
                            person.id in ids,
                    )
                }
        val at = record.messages.indexOfFirst { it.id == message.id }
        record.messages[at] = message.copy(reactions = reactions, cursor = ++record.changeSeq)
        val payload =
            JsonObject(
                mapOf(
                    "message_id" to JsonPrimitive(message.id),
                    "reactions" to
                        output.encodeToJsonElement(
                            ListSerializer(Reaction.serializer()),
                            reactions,
                        ),
                ),
            )
        sockets.toList().forEach {
            it.push(
                record.channel,
                "reaction.changed",
                payload,
            )
        }
        return ok(ListBody(reactions))
    }

    /** What was reported: reporter, message, reason (MA-04). */
    fun reportsOf(messageId: String): List<String> =
        synchronized(lock) { reports.filter { it.second == messageId }.map { it.third } }

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
        // The window from the open message's opening (idejo's start_new_at, simplified).
        val opened = composer.lastOpenedAt.takeIf { composer.openMessageId != null }
        return ComposerState(
            openMessageId = composer.openMessageId,
            mayStartNewAt = held ?: opened?.plusSeconds(windowOf(record).toLong()),
            heldUntil = held,
            typingSince = composer.typingSince,
            windowSeconds = windowOf(record),
            unansweredSeconds = if (record.topic.private) 0 else settings.unansweredSeconds,
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
        val text = punctuate(person, commit.bodyMd)

        if (commit.new && open != null) {
            // A reply while a message is open: it closes, and the reply starts at once if
            // early replies are allowed, else not before the timers run out.
            if (!settings.earlyReply && withinWindow(record, composer)) return tooSoon()
            close(record, open, held = false)
            composer.openMessageId = null
        } else if (open == null && held != null) {
            return tooSoon()
        }

        val message =
            if (composer.openMessageId == null) {
                if (text.isBlank()) return ok(CommitResult(null, composerState(record, person)))
                opened(record, person, composer, text, commit.replyTo)
            } else {
                append(record, requireNotNull(composer.openMessageId), text)
            }

        val result =
            if (commit.breakMessage) {
                val early = withinWindow(record, composer)
                composer.openMessageId = null
                if (early) {
                    composer.heldUntil =
                        composer.lastOpenedAt?.plusSeconds(windowOf(record).toLong())
                }
                close(record, message.id, held = early)
            } else {
                message
            }
        return ok(CommitResult(result, composerState(record, person)))
    }

    /** A message the commit opens; in a conversation, news for the other participant. */
    private fun opened(
        record: TopicRecord,
        person: Person,
        composer: ComposerRecord,
        text: String,
        replyTo: List<String>,
    ): Message {
        val message = openMessage(record, person, text, replyTo)
        composer.openMessageId = message.id
        composer.lastOpenedAt = message.openedAt
        composer.typingSince = null
        if (record.topic.private) conversationNews(record, person)
        return message
    }

    private fun withinWindow(
        record: TopicRecord,
        composer: ComposerRecord,
    ): Boolean =
        composer.lastOpenedAt?.let {
            Duration.between(it, clock.now).seconds < windowOf(record)
        }
            ?: false

    /** A conversation has no slow mode (PC-01): its window and unanswered time are 0. */
    private fun windowOf(record: TopicRecord): Int =
        if (record.topic.private) 0 else settings.windowSeconds

    private fun tooSoon() =
        error(
            HTTP_CONFLICT,
            ApiError.TOO_SOON,
            "You cannot post a new message yet. Append your old message.",
        )

    /** The server adds a full stop after unpunctuated text (API guide §6). */
    private fun punctuate(
        author: Person,
        text: String,
    ): String {
        val trimmed = text.trimEnd()
        val on = settings.autoPunctuation && reader(author).autoPunctuation
        if (!on || trimmed.isEmpty()) return trimmed
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
        sockets.toList().forEach { it.push(record.channel, event, payload) }
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

    /** A conversation's channel: its participants only (PC-01). */
    internal fun joinConversation(
        id: String,
        token: String,
    ): JsonObject? =
        synchronized(lock) {
            val record = topics[id]?.takeIf { it.topic.private } ?: return null
            if (tokens[token]?.id !in record.participants) return null
            buildJsonObject { put("cursor", record.changeSeq) }
        }

    /** A topic channel's join: the topic must exist and be visible. */
    internal fun joinTopic(id: String): JsonObject? =
        synchronized(lock) {
            topics[id]?.takeIf { !it.topic.private }?.let { record ->
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
        val deviceName: String? = null,
        var used: Boolean = false,
        var token: String? = null,
    )

    /** A token as **Apps and devices** lists it (ST-05). */
    private class DeviceRecord(
        val token: String,
        val name: String,
        val signedInAt: Instant,
    )

    private companion object {
        /** An 87-character VAPID public key, as idejo gives it. */
        val FAKE_VAPID_KEY = "B" + "A".repeat(86)
        const val PASSWORD_MIN = 12
        const val API = "/api/v1"
        const val DIRECTORY = "/directory"
        const val DIRECTORY_PAGE = 20
        const val AUTH_TOKEN_PREFIX = "base64url.bearer.phx."
        const val CHALLENGE_LENGTH = 43
        val CODE_LIFETIME: Duration = Duration.ofSeconds(60)
        const val DEFAULT_PAGE = 50
        const val MAX_PAGE = 100

        /** idejo's Normal sizes: messages before and after the landing one. */
        const val WINDOW_BEFORE = 10
        const val WINDOW_AFTER = 30
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

private val FAKE_EMOJI =
    listOf(
        Emoji("joy", "😂"),
        Emoji("tada", "🎉"),
        Emoji("+1", "👍"),
        Emoji("agree", imageUrl = "/emoji/agree.png"),
    )

private const val MAX_HOURS = 168.0
private const val SECONDS_PER_HOUR = 3600
private const val HTTP_UNPROCESSABLE_ENTITY = 422

private val FAKE_COMMANDS =
    listOf("image", "gif", "new-topic", "poll", "schedule", "spoiler", "forward").map {
        org.idejo.app.core.model
            .Command(it, "builtin")
    } +
        org.idejo.app.core.model
            .Command("youtube", "plugin", argument = "link", block = "video")

/** The fake's `upload_max_bytes` (CO-14). */
const val FAKE_UPLOAD_MAX_BYTES = 8_000_000L

/** The characters of the fake's reaction emoji. */
private val FAKE_EMOJI_CHARS = mapOf("heart" to "❤️", "tada" to "🎉", "+1" to "👍", "joy" to "😂")
