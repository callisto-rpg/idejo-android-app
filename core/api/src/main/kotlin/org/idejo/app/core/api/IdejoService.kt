// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.api

import org.idejo.app.core.model.Category
import org.idejo.app.core.model.Changes
import org.idejo.app.core.model.Command
import org.idejo.app.core.model.Commit
import org.idejo.app.core.model.CommitResult
import org.idejo.app.core.model.Community
import org.idejo.app.core.model.ComposerState
import org.idejo.app.core.model.IntroClose
import org.idejo.app.core.model.ListBody
import org.idejo.app.core.model.Me
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.MessageWindow
import org.idejo.app.core.model.Page
import org.idejo.app.core.model.Poll
import org.idejo.app.core.model.ReadPosition
import org.idejo.app.core.model.RoleCard
import org.idejo.app.core.model.SearchHit
import org.idejo.app.core.model.Server
import org.idejo.app.core.model.Tag
import org.idejo.app.core.model.Topic
import org.idejo.app.core.model.TopicUpdate
import org.idejo.app.core.model.Unread
import org.idejo.app.core.model.UserCard
import org.idejo.app.core.model.Vote
import org.idejo.app.core.model.Warning
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The endpoints of API v1 the app uses so far (the OpenAPI document at
 * `/api/v1/openapi.json` is the reference). Added to as milestones need them.
 */
internal interface IdejoService {
    @GET("server")
    suspend fun server(): Server

    @GET("me")
    suspend fun me(): Me

    @GET("me/communities")
    suspend fun communities(): ListBody<Community>

    @GET("categories")
    suspend fun categories(): ListBody<Category>

    @GET("categories/{id}")
    suspend fun category(
        @Path("id") id: String,
    ): Category

    @GET("categories/{id}/topics")
    suspend fun categoryTopics(
        @Path("id") id: String,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int?,
        @Query("expanded") expanded: Boolean?,
    ): Page<Topic>

    @GET("topics/active")
    suspend fun activeTopics(
        @Query("expanded") expanded: Boolean?,
    ): ListBody<Topic>

    @GET("topics/{id}")
    suspend fun topic(
        @Path("id") id: String,
    ): Topic

    @GET("topics/{id}/messages")
    suspend fun messages(
        @Path("id") id: String,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int?,
        @Query("around") around: String?,
        @Query("first_unread") firstUnread: Boolean?,
    ): MessageWindow

    @GET("topics/{id}/changes")
    suspend fun changes(
        @Path("id") id: String,
        @Query("since") since: Long,
        @Query("limit") limit: Int?,
    ): Changes

    @GET("topics/{id}/pins")
    suspend fun pins(
        @Path("id") id: String,
    ): ListBody<Message>

    @GET("messages/{id}")
    suspend fun message(
        @Path("id") id: String,
    ): Message

    @GET("topics/{id}/composer")
    suspend fun composer(
        @Path("id") id: String,
    ): ComposerState

    @POST("topics/{id}/composer/typing")
    suspend fun typing(
        @Path("id") id: String,
    ): ComposerState

    @POST("topics/{id}/composer/commit")
    suspend fun commit(
        @Path("id") id: String,
        @Body commit: Commit,
    ): CommitResult

    @GET("tags")
    suspend fun tags(): ListBody<Tag>

    @GET("topics")
    suspend fun tagged(
        @Query("tags") tags: String,
        @Query("operator") operator: String?,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int?,
    ): Page<Topic>

    @GET("unread")
    suspend fun unread(
        @Query("cursor") cursor: String?,
    ): Unread

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("category_id") categoryId: String?,
        @Query("cursor") cursor: String?,
    ): Page<SearchHit>

    @GET("users/{id}")
    suspend fun user(
        @Path("id") id: String,
    ): UserCard

    @GET("roles/{id}")
    suspend fun role(
        @Path("id") slug: String,
    ): RoleCard

    @PUT("users/{id}/ignore")
    suspend fun ignore(
        @Path("id") id: String,
    ): UserCard

    @DELETE("users/{id}/ignore")
    suspend fun unignore(
        @Path("id") id: String,
    ): UserCard

    @GET("me/warnings")
    suspend fun warnings(): ListBody<Warning>

    @POST("me/warnings/acknowledge")
    suspend fun acknowledgeWarnings(): retrofit2.Response<Unit>

    @POST("me/welcome")
    suspend fun welcome(): Me

    @POST("me/intro")
    suspend fun intro(
        @Body body: IntroClose,
    ): Me

    @PUT("me/roles/{id}")
    suspend fun addRole(
        @Path("id") id: String,
    ): Me

    @DELETE("me/roles/{id}")
    suspend fun removeRole(
        @Path("id") id: String,
    ): Me

    @GET("topics/{id}/commands")
    suspend fun commands(
        @Path("id") id: String,
    ): ListBody<Command>

    @PUT("topics/{id}/read")
    suspend fun read(
        @Path("id") id: String,
        @Body position: ReadPosition,
    ): Topic

    @PUT("polls/{id}/vote")
    suspend fun vote(
        @Path("id") id: String,
        @Body vote: Vote,
    ): Poll

    @PUT("topics/{id}/follow")
    suspend fun follow(
        @Path("id") id: String,
    ): Topic

    @DELETE("topics/{id}/follow")
    suspend fun unfollow(
        @Path("id") id: String,
    ): Topic

    @PUT("topics/{id}/mute")
    suspend fun mute(
        @Path("id") id: String,
    ): Topic

    @DELETE("topics/{id}/mute")
    suspend fun unmute(
        @Path("id") id: String,
    ): Topic

    @POST("topics/{id}/mark-unread")
    suspend fun markUnread(
        @Path("id") id: String,
    ): Topic

    @PATCH("topics/{id}")
    suspend fun updateTopic(
        @Path("id") id: String,
        @Body update: TopicUpdate,
    ): Topic
}
