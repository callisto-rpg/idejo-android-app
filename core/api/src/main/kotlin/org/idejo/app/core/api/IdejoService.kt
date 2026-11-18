// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.api

import okhttp3.MultipartBody
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
import org.idejo.app.core.model.ForwardTarget
import org.idejo.app.core.model.GifSettings
import org.idejo.app.core.model.IntroClose
import org.idejo.app.core.model.ListBody
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
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
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

    @PUT("me/password")
    suspend fun changePassword(
        @Body body: PasswordChange,
    ): Response<Unit>

    @Multipart
    @PUT("me/avatar")
    suspend fun setAvatar(
        @Part image: MultipartBody.Part,
    ): Me

    @Multipart
    @PUT("me/avatar-dark")
    suspend fun setAvatarDark(
        @Part image: MultipartBody.Part,
    ): Me

    @DELETE("me/avatar")
    suspend fun removeAvatar(): Me

    @DELETE("me/avatar-dark")
    suspend fun removeAvatarDark(): Me

    @GET("me/tokens")
    suspend fun tokens(): ListBody<Token>

    @DELETE("me/tokens/{id}")
    suspend fun deleteToken(
        @Path("id") id: String,
    ): Response<Unit>

    @GET("me/notification-settings")
    suspend fun notificationSettings(): NotificationSettings

    @PATCH("me/notification-settings")
    suspend fun updateNotificationSettings(
        @Body settings: NotificationSettings,
    ): NotificationSettings

    @GET("me/ignored")
    suspend fun ignored(): ListBody<Person>

    @GET("me/push")
    suspend fun push(): ListBody<PushRegistration>

    @POST("me/push/unifiedpush")
    suspend fun registerPush(
        @Body body: UnifiedPushSubscription,
    ): PushRegistration

    @DELETE("me/push/unifiedpush/{id}")
    suspend fun deletePush(
        @Path("id") id: String,
    ): Response<Unit>

    @POST("me/push/test")
    suspend fun testPush(): PushTest

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

    @PUT("messages/{id}")
    suspend fun editMessage(
        @Path("id") id: String,
        @Body edit: MessageEdit,
    ): Message

    @DELETE("messages/{id}")
    suspend fun deleteMessage(
        @Path("id") id: String,
    ): Message

    @GET("emoji")
    suspend fun emoji(
        @Query("q") query: String?,
    ): ListBody<Emoji>

    @GET("suggest")
    suspend fun suggest(
        @Query("sigil") sign: String,
        @Query("q") query: String,
        @Query("topic_id") topicId: String?,
    ): ListBody<Suggestion>

    @GET("forward-targets")
    suspend fun forwardTargets(
        @Query("q") query: String?,
        @Query("except") except: String?,
    ): ListBody<ForwardTarget>

    @GET("tags")
    suspend fun tags(): ListBody<Tag>

    @GET("topics")
    suspend fun tagged(
        @Query("tags") tags: String,
        @Query("operator") operator: String?,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int?,
    ): Page<Topic>

    @GET("me/followed")
    suspend fun followed(
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

    @GET("topic-icons")
    suspend fun topicIcons(): ListBody<TopicIcon>

    @GET("gifs")
    suspend fun gifs(): GifSettings

    @POST("categories/{id}/topics")
    suspend fun createTopic(
        @Path("id") categoryId: String,
        @Body body: TopicCreate,
    ): TopicCreated

    @POST("messages/{id}/new-topic")
    suspend fun forkTopic(
        @Path("id") messageId: String,
        @Body body: TopicCreate,
    ): TopicCreated

    @Multipart
    @POST("uploads")
    suspend fun upload(
        @Part image: MultipartBody.Part,
    ): Upload

    @GET("topics/{id}/scheduled")
    suspend fun scheduled(
        @Path("id") id: String,
    ): ListBody<Scheduled>

    @POST("topics/{id}/scheduled")
    suspend fun schedule(
        @Path("id") id: String,
        @Body body: ScheduleCreate,
    ): Scheduled

    @PUT("messages/{id}/reactions/{emoji}")
    suspend fun react(
        @Path("id") id: String,
        @Path("emoji") emoji: String,
    ): ListBody<Reaction>

    @DELETE("messages/{id}/reactions/{emoji}")
    suspend fun unreact(
        @Path("id") id: String,
        @Path("emoji") emoji: String,
    ): ListBody<Reaction>

    @GET("messages/{id}/reactions")
    suspend fun reactors(
        @Path("id") id: String,
    ): ListBody<Reactor>

    @POST("messages/{id}/report")
    suspend fun report(
        @Path("id") id: String,
        @Body body: ReportBody,
    ): Response<Unit>

    @GET("conversations")
    suspend fun conversations(): ListBody<Conversation>

    @GET("conversations/{id}")
    suspend fun conversation(
        @Path("id") id: String,
    ): Conversation

    @POST("conversations")
    suspend fun startConversation(
        @Body body: ConversationStart,
    ): Conversation

    @PATCH("me")
    suspend fun updateMe(
        @Body body: MePatch,
    ): Me

    @DELETE("scheduled/{id}")
    suspend fun cancelScheduled(
        @Path("id") id: String,
    ): Response<Unit>

    @POST("topics/{id}/polls")
    suspend fun createPoll(
        @Path("id") id: String,
        @Body body: PollCreate,
    ): Message

    @PUT("messages/{id}/spoiler")
    suspend fun spoiler(
        @Path("id") id: String,
        @Body body: SpoilerSet,
    ): Message

    @DELETE("messages/{id}/spoiler")
    suspend fun unspoiler(
        @Path("id") id: String,
    ): Message

    @POST("topics/{id}/plugins/{command}")
    suspend fun runPlugin(
        @Path("id") id: String,
        @Path("command") command: String,
        @Body body: PluginRun,
    ): Message

    @POST("topics/{id}/automessages/{name}")
    suspend fun automessage(
        @Path("id") id: String,
        @Path("name") name: String,
    ): Message

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
