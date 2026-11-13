// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.api

import org.idejo.app.core.model.Category
import org.idejo.app.core.model.Changes
import org.idejo.app.core.model.Commit
import org.idejo.app.core.model.CommitResult
import org.idejo.app.core.model.Community
import org.idejo.app.core.model.ComposerState
import org.idejo.app.core.model.ListBody
import org.idejo.app.core.model.Me
import org.idejo.app.core.model.Message
import org.idejo.app.core.model.MessageWindow
import org.idejo.app.core.model.Page
import org.idejo.app.core.model.Server
import org.idejo.app.core.model.Topic
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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
    suspend fun activeTopics(): ListBody<Topic>

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
}
