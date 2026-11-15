// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.api

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.idejo.app.core.model.AccessToken
import org.idejo.app.core.model.Category
import org.idejo.app.core.model.Changes
import org.idejo.app.core.model.Command
import org.idejo.app.core.model.Commit
import org.idejo.app.core.model.CommitResult
import org.idejo.app.core.model.Community
import org.idejo.app.core.model.ComposerState
import org.idejo.app.core.model.Emoji
import org.idejo.app.core.model.ForwardTarget
import org.idejo.app.core.model.IntroClose
import org.idejo.app.core.model.Me
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.MessageEdit
import org.idejo.app.core.model.MessageWindow
import org.idejo.app.core.model.Page
import org.idejo.app.core.model.Poll
import org.idejo.app.core.model.ReadPosition
import org.idejo.app.core.model.RoleCard
import org.idejo.app.core.model.SearchHit
import org.idejo.app.core.model.Server
import org.idejo.app.core.model.Suggestion
import org.idejo.app.core.model.Tag
import org.idejo.app.core.model.Topic
import org.idejo.app.core.model.TopicUpdate
import org.idejo.app.core.model.Unread
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
