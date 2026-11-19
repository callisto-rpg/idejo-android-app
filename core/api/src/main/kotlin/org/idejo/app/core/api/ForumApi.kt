// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.api

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.idejo.app.core.model.AccessToken
import org.idejo.app.core.model.Category
import org.idejo.app.core.model.Changes
import org.idejo.app.core.model.Command
import org.idejo.app.core.model.Commit
import org.idejo.app.core.model.CommitResult
import org.idejo.app.core.model.Community
import org.idejo.app.core.model.ComposerState
import org.idejo.app.core.model.Conversation
import org.idejo.app.core.model.ConversationStart
import org.idejo.app.core.model.Emoji
import org.idejo.app.core.model.EverywhereRequest
import org.idejo.app.core.model.EverywhereResult
import org.idejo.app.core.model.ForwardTarget
import org.idejo.app.core.model.GifSettings
import org.idejo.app.core.model.IntroClose
import org.idejo.app.core.model.Me
import org.idejo.app.core.model.MePatch
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.MessageEdit
import org.idejo.app.core.model.MessageWindow
import org.idejo.app.core.model.NotificationSettings
import org.idejo.app.core.model.Page
import org.idejo.app.core.model.PasswordChange
import org.idejo.app.core.model.Person
import org.idejo.app.core.model.PluginRun
import org.idejo.app.core.model.Poll
import org.idejo.app.core.model.PollCreate
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
import org.idejo.app.core.model.SpoilerSet
import org.idejo.app.core.model.Suggestion
import org.idejo.app.core.model.Tag
import org.idejo.app.core.model.Token
import org.idejo.app.core.model.Topic
import org.idejo.app.core.model.TopicCreate
import org.idejo.app.core.model.TopicCreated
import org.idejo.app.core.model.TopicIcon
import org.idejo.app.core.model.TopicUpdate
import org.idejo.app.core.model.UnifiedPushSubscription
import org.idejo.app.core.model.Unread
import org.idejo.app.core.model.Upload
import org.idejo.app.core.model.UserCard
import org.idejo.app.core.model.Vote
import org.idejo.app.core.model.Warning
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.time.Instant

/** The token of one forum, read when a request leaves; null for a visitor (SI-05). */
fun interface TokenSource {
    fun token(): AccessToken?
}

/**
 * One forum's API (v1). Its client sends the token as `Authorization: Bearer`, and only
 * to the forum's own origin: any other request it is asked to make leaves without it
 * (SI-04, SY-04).
 */
class ForumApi(
    val address: ForumAddress,
    apiBase: String,
    tokens: TokenSource,
    http: OkHttpClient,
) {
    private val service: IdejoService =
        Retrofit
            .Builder()
            .baseUrl(
                requireNotNull(address.resolve(apiBase.trimEnd('/') + "/")) {
                    "The API is off the forum."
                },
            ).client(
                http
                    .newBuilder()
                    .addInterceptor(BearerInterceptor(address, tokens))
                    .build(),
            ).addConverterFactory(
                IdejoJson
                    .asConverterFactory("application/json".toMediaType()),
            ).build()
            .create(IdejoService::class.java)

    suspend fun server(): ApiResult<Server> = call { server() }

    suspend fun me(): ApiResult<Me> = call { me() }

    /**
     * Changes the password (ST-04). Every device of the account signs in again, this one
     * too: its token stops working once the server agrees.
     */
    suspend fun changePassword(change: PasswordChange): ApiResult<Unit> =
        call { changePassword(change).let { if (!it.isSuccessful) throw HttpException(it) } }

    /**
     * The member's picture (ST-01), or the dark theme's with [dark]: a square image as
     * multipart `image`; the server answers with `GET /me`.
     */
    suspend fun setPicture(
        dark: Boolean,
        bytes: ByteArray,
        type: String,
    ): ApiResult<Me> =
        call {
            val part =
                MultipartBody.Part.createFormData(
                    "image",
                    "picture." + type.substringAfter('/').replace("jpeg", "jpg"),
                    bytes.toRequestBody(type.toMediaTypeOrNull()),
                )
            if (dark) setAvatarDark(part) else setAvatar(part)
        }

    /** Removes the picture, or the dark theme's with [dark] (ST-01). */
    suspend fun removePicture(dark: Boolean): ApiResult<Me> =
        call { if (dark) removeAvatarDark() else removeAvatar() }

    /** The devices signed in to the app, this one marked (ST-05, AP-09). */
    suspend fun tokens(): ApiResult<List<Token>> = call { tokens().data }

    /** Signs a device out (ST-05, AP-09). */
    suspend fun deleteToken(id: String): ApiResult<Unit> =
        call { deleteToken(id).let { if (!it.isSuccessful) throw HttpException(it) } }

    /** The email summary's and push choices (ST-03, NT-08). */
    suspend fun notificationSettings(): ApiResult<NotificationSettings> =
        call { notificationSettings() }

    /** Saves the choices sent and leaves the others as they are (ST-03, NT-08). */
    suspend fun updateNotificationSettings(
        settings: NotificationSettings,
    ): ApiResult<NotificationSettings> = call { updateNotificationSettings(settings) }

    /** The members the member ignores (ST-08); [ignore] takes one off the list. */
    suspend fun ignored(): ApiResult<List<Person>> = call { ignored().data }

    /** The browsers and devices taking push (NT-08). */
    suspend fun pushRegistrations(): ApiResult<List<PushRegistration>> = call { push().data }

    /** This device's distributor endpoint and keys, for the forum's pushes (NT-02). */
    suspend fun registerPush(subscription: UnifiedPushSubscription): ApiResult<PushRegistration> =
        call { registerPush(subscription) }

    /** Ends one push registration (NT-08). */
    suspend fun deletePushRegistration(id: String): ApiResult<Unit> =
        call { deletePush(id).let { if (!it.isSuccessful) throw HttpException(it) } }

    /** A test notification to every device (NT-08). */
    suspend fun testPush(): ApiResult<PushTest> = call { testPush() }

    suspend fun communities(): ApiResult<List<Community>> = call { communities().data }

    suspend fun categories(): ApiResult<List<Category>> = call { categories().data }

    suspend fun category(id: String): ApiResult<Category> = call { category(id) }

    suspend fun categoryTopics(
        id: String,
        cursor: String? = null,
        expanded: Boolean = false,
        limit: Int? = null,
    ): ApiResult<Page<Topic>> = call { categoryTopics(id, cursor, limit, expanded.takeIf { it }) }

    /** The home page's active topics; [expanded] adds previews (idejo 0.3.13, BR-03). */
    suspend fun activeTopics(expanded: Boolean = false): ApiResult<List<Topic>> =
        call { activeTopics(expanded.takeIf { it }).data }

    suspend fun topic(id: String): ApiResult<Topic> = call { topic(id) }

    /** A window of messages: the latest, around a message, at the first unread, or a page. */
    suspend fun messages(
        topicId: String,
        cursor: String? = null,
        around: String? = null,
        firstUnread: Boolean = false,
        limit: Int? = null,
    ): ApiResult<MessageWindow> =
        call {
            messages(topicId, cursor, limit, around, firstUnread.takeIf { it })
        }

    /** Catch-up after a disconnect (API guide §7); `410 cursor_expired`: load the topic again. */
    suspend fun changes(
        topicId: String,
        since: Long,
        limit: Int? = null,
    ): ApiResult<Changes> = call { changes(topicId, since, limit) }

    /** The topics the member follows, in the order followed (IX-02, idejo 0.3.16). */
    suspend fun followed(
        cursor: String? = null,
        limit: Int? = null,
    ): ApiResult<Page<Topic>> = call { followed(cursor, limit) }

    suspend fun pins(topicId: String): ApiResult<List<Message>> = call { pins(topicId).data }

    suspend fun message(id: String): ApiResult<Message> = call { message(id) }

    suspend fun composer(topicId: String): ApiResult<ComposerState> = call { composer(topicId) }

    suspend fun typing(topicId: String): ApiResult<ComposerState> = call { typing(topicId) }

    suspend fun commit(
        topicId: String,
        commit: Commit,
    ): ApiResult<CommitResult> = call { commit(topicId, commit) }

    /** Edits one's closed message (CO-18); [finish] ends the edit (Ctrl+Enter). */
    suspend fun editMessage(
        id: String,
        bodyMd: String,
        finish: Boolean,
    ): ApiResult<Message> = call { editMessage(id, MessageEdit(bodyMd, finish)) }

    /** Deletes one's own message (CO-18), not a topic's first. */
    suspend fun deleteMessage(id: String): ApiResult<Message> = call { deleteMessage(id) }

    /** Emoji by name (the emoji picker, CO-10). */
    suspend fun emoji(query: String?): ApiResult<List<Emoji>> =
        call { emoji(query?.ifBlank { null }).data }

    /** The composer's suggestions after [sign] (CO-12), the recent ones first. */
    suspend fun suggest(
        sign: Char,
        query: String,
        topicId: String?,
    ): ApiResult<List<Suggestion>> = call { suggest(sign.toString(), query, topicId).data }

    /** Where a message may be forwarded (CO-17), matching [query], leaving out [except]. */
    suspend fun forwardTargets(
        query: String? = null,
        except: String? = null,
    ): ApiResult<List<ForwardTarget>> = call { forwardTargets(query, except).data }

    suspend fun tags(): ApiResult<List<Tag>> = call { tags().data }

    /** Topics carrying [tags] (slugs), all of them or, with [any], one of them (BR-04). */
    suspend fun tagged(
        tags: List<String>,
        any: Boolean = false,
        cursor: String? = null,
        limit: Int? = null,
    ): ApiResult<Page<Topic>> =
        call {
            tagged(tags.joinToString(","), if (any) "or" else null, cursor, limit)
        }

    suspend fun unread(cursor: String? = null): ApiResult<Unread> = call { unread(cursor) }

    suspend fun search(
        query: String,
        categoryId: String? = null,
        cursor: String? = null,
    ): ApiResult<Page<SearchHit>> = call { search(query, categoryId, cursor) }

    suspend fun user(id: String): ApiResult<UserCard> = call { user(id) }

    /** A role, its member count and members, by slug (TV-22). */
    suspend fun roleCard(slug: String): ApiResult<RoleCard> = call { role(slug) }

    suspend fun ignore(
        id: String,
        ignored: Boolean,
    ): ApiResult<UserCard> = call { if (ignored) ignore(id) else unignore(id) }

    suspend fun warnings(): ApiResult<List<Warning>> = call { warnings().data }

    suspend fun acknowledgeWarnings(): ApiResult<Unit> =
        call {
            acknowledgeWarnings().let { if (!it.isSuccessful) throw HttpException(it) }
        }

    /** The welcome card (About, Rules, roles) seen (BR-07). */
    suspend fun welcome(): ApiResult<Me> = call { welcome() }

    /** Getting started closed: until the next sign-in, or with [hide] for good (BR-11). */
    suspend fun intro(hide: Boolean): ApiResult<Me> = call { intro(IntroClose(hide)) }

    /** A self-select role taken or left (BR-07). */
    suspend fun role(
        id: String,
        taken: Boolean,
    ): ApiResult<Me> = call { if (taken) addRole(id) else removeRole(id) }

    suspend fun commands(topicId: String): ApiResult<List<Command>> =
        call { commands(topicId).data }

    /** Whether GIFs are on, and the provider the app calls itself (CO-15). */
    suspend fun gifs(): ApiResult<GifSettings> = call { gifs() }

    /**
     * The icons a topical topic takes (CO-19). The API files them under the `moderate`
     * scope, which every token of the app has; the server checks no role for the list.
     */
    suspend fun topicIcons(): ApiResult<List<TopicIcon>> = call { topicIcons().data }

    /** A new topic (CO-19); continuing from message [from] when given. */
    suspend fun createTopic(
        body: TopicCreate,
        from: String? = null,
    ): ApiResult<TopicCreated> =
        call {
            if (from != null) {
                forkTopic(from, body)
            } else {
                createTopic(requireNotNull(body.categoryId), body.copy(categoryId = null))
            }
        }

    /** An image for the composer (CO-14): JPEG, PNG, GIF or WebP, as multipart `image`. */
    suspend fun upload(
        bytes: ByteArray,
        fileName: String,
        type: String,
    ): ApiResult<Upload> =
        call {
            val body = bytes.toRequestBody(type.toMediaTypeOrNull())
            upload(MultipartBody.Part.createFormData("image", fileName, body))
        }

    /** The member's scheduled messages in a topic (CO-20). */
    suspend fun scheduled(topicId: String): ApiResult<List<Scheduled>> =
        call { scheduled(topicId).data }

    suspend fun schedule(
        topicId: String,
        body: ScheduleCreate,
    ): ApiResult<Scheduled> = call { schedule(topicId, body) }

    /** A reaction added or taken back (MA-03), by the emoji's name; the message's reactions. */
    suspend fun react(
        messageId: String,
        name: String,
        on: Boolean,
    ): ApiResult<List<Reaction>> =
        call { (if (on) react(messageId, name) else unreact(messageId, name)).data }

    /** Who reacted to a message (MA-03). */
    suspend fun reactors(messageId: String): ApiResult<List<Reactor>> =
        call { reactors(messageId).data }

    /** The member's private conversations (PC-01), the latest active first. */
    suspend fun conversations(): ApiResult<List<Conversation>> = call { conversations().data }

    /** One conversation's card: who it is with (PC-01). */
    suspend fun conversation(id: String): ApiResult<Conversation> = call { conversation(id) }

    /** **Private Message** (PC-03): the conversation with [userId], started or found. */
    suspend fun startConversation(userId: String): ApiResult<Conversation> =
        call { startConversation(ConversationStart(userId = userId)) }

    /** On a group's inbox (IB-07, IX-04): the conversation with idejo ID [subject]. */
    suspend fun startConversationWith(subject: String): ApiResult<Conversation> =
        call { startConversation(ConversationStart(subject = subject)) }

    /** The member's choices (ST, PC-03): only [patch]'s fields change; answers `GET /me`. */
    suspend fun updateMe(patch: MePatch): ApiResult<Me> = call { updateMe(patch) }

    /**
     * On a group's inbox (ST-07, IX-17): [sections] as they are here, saved on every forum
     * the member joined, as the web's **Save for all forums**.
     */
    suspend fun saveEverywhere(sections: List<String>): ApiResult<EverywhereResult> =
        call { saveEverywhere(EverywhereRequest(sections)) }

    /** A message reported to the moderators (MA-04). */
    suspend fun report(
        messageId: String,
        reason: String,
    ): ApiResult<Unit> =
        call {
            report(
                messageId,
                ReportBody(reason),
            ).let { if (!it.isSuccessful) throw HttpException(it) }
        }

    suspend fun cancelScheduled(id: String): ApiResult<Unit> =
        call { cancelScheduled(id).let { if (!it.isSuccessful) throw HttpException(it) } }

    suspend fun createPoll(
        topicId: String,
        body: PollCreate,
    ): ApiResult<Message> = call { createPoll(topicId, body) }

    /** `!spoiler` (CO-13): on, with [overlay]'s words, or off. */
    suspend fun spoiler(
        messageId: String,
        on: Boolean,
        overlay: String? = null,
    ): ApiResult<Message> =
        call { if (on) spoiler(messageId, SpoilerSet(overlay)) else unspoiler(messageId) }

    suspend fun runPlugin(
        topicId: String,
        command: String,
        body: PluginRun,
    ): ApiResult<Message> = call { runPlugin(topicId, command, body) }

    suspend fun automessage(
        topicId: String,
        name: String,
    ): ApiResult<Message> = call { automessage(topicId, name) }

    /** Read up to [messageId], or all of the topic (TV-06). */
    suspend fun read(
        topicId: String,
        messageId: String? = null,
    ): ApiResult<Topic> = call { read(topicId, ReadPosition(messageId)) }

    /** Votes, changes or takes back (no [optionIds]) a vote (TV-27). */
    suspend fun vote(
        pollId: String,
        optionIds: List<String>,
    ): ApiResult<Poll> = call { vote(pollId, Vote(optionIds)) }

    suspend fun follow(
        topicId: String,
        followed: Boolean,
    ): ApiResult<Topic> = call { if (followed) follow(topicId) else unfollow(topicId) }

    suspend fun mute(
        topicId: String,
        muted: Boolean,
    ): ApiResult<Topic> = call { if (muted) mute(topicId) else unmute(topicId) }

    suspend fun markUnread(topicId: String): ApiResult<Topic> = call { markUnread(topicId) }

    suspend fun rename(
        topicId: String,
        title: String,
    ): ApiResult<Topic> = call { updateTopic(topicId, TopicUpdate(title = title)) }

    @Suppress("TooGenericExceptionCaught") // Retrofit wraps every failure; each is mapped.
    private suspend fun <T> call(request: suspend IdejoService.() -> T): ApiResult<T> =
        try {
            ApiResult.Success(service.request())
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            ApiResult.Failure(problemFrom(e))
        } catch (e: IOException) {
            ApiResult.Failure(ApiProblem.Network(e))
        } catch (e: SerializationException) {
            ApiResult.Failure(ApiProblem.Malformed(HTTP_OK, e))
        } catch (e: IllegalArgumentException) {
            ApiResult.Failure(ApiProblem.Malformed(HTTP_OK, e))
        }

    private fun problemFrom(e: HttpException): ApiProblem {
        val response = e.response() ?: return ApiProblem.Malformed(e.code())
        val body = response.errorBody()?.use { it.string() }.orEmpty()
        val retryAfter = RetryAfter.parse(response.headers()["Retry-After"], Instant.now())
        return errorProblem(e.code(), body, retryAfter)
    }

    private companion object {
        const val HTTP_OK = 200
    }
}

/** Adds the token to requests for its own forum, and removes any from the rest. */
internal class BearerInterceptor(
    private val address: ForumAddress,
    private val tokens: TokenSource,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val builder = request.newBuilder().removeHeader("Authorization")
        if (address.isSameOrigin(request.url)) {
            tokens.token()?.let { builder.header("Authorization", "Bearer ${it.value}") }
        }
        return chain.proceed(builder.build())
    }
}
