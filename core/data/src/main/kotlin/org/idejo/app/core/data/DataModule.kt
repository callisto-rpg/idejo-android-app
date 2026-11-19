// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.data

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.idejo.app.core.api.IdejoHttp
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

/** The app's `User-Agent`, `idejo-android/<version>` (provided by the app module). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UserAgent

/** The application's own scope, for work that outlives a screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions") // One provider per app-wide object.
object DataModule {
    @Provides
    @Singleton
    @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun http(
        @UserAgent userAgent: String,
    ): OkHttpClient = IdejoHttp.newClient(userAgent)

    @Provides
    @Singleton
    fun store(
        @ApplicationContext context: Context,
        @AppScope scope: CoroutineScope,
    ): ForumStore = ForumStore.create(File(context.filesDir, "datastore/app.json"), scope)

    @Provides
    @Singleton
    fun vault(): TokenVault = TokenVault(KeystoreBox())

    @Provides
    @Singleton
    fun sessions(
        store: ForumStore,
        vault: TokenVault,
        http: OkHttpClient,
        @AppScope scope: CoroutineScope,
        pages: PageCache,
    ): SessionManager =
        SessionManager(store, vault, http, scope, Dispatchers.Default, pages).also { manager ->
            scope.launch { manager.forgotten.collect { pages.forget(it) } }
        }

    /** The forums' last answers (OF-01), within App settings' storage limit (ST-21). */
    @Provides
    @Singleton
    fun pages(
        @ApplicationContext context: Context,
        choices: AppChoices,
    ): PageCache =
        PageCache(PageCache.database(context).pages(), limitBytes = {
            choices.cacheLimitMb.first() * MEGABYTE
        })

    @Provides
    @Singleton
    fun signIn(
        sessions: SessionManager,
        http: OkHttpClient,
    ): SignInCoordinator = SignInCoordinator(sessions, http)

    /** The inbox's Forums and Topics (IB-02, IB-03), read from every connected forum. */
    @Provides
    @Singleton
    fun inboxTopics(sessions: SessionManager): InboxTopics = InboxTopics(sessions)

    /** idejo ID mode's forums (SI-06): `device_name` as at any sign-in (SI-03). */
    @Provides
    @Singleton
    fun groupForums(
        sessions: SessionManager,
        signIn: SignInCoordinator,
        discovery: org.idejo.app.core.api.DiscoveryClient,
        http: OkHttpClient,
    ): GroupForums =
        GroupForums(
            sessions,
            signIn,
            discovery,
            org.idejo.app.core.api
                .DirectoryClient(http),
            android.os.Build.MODEL
                ?.takeIf { it.isNotBlank() } ?: "Android",
        )

    @Provides
    @Singleton
    fun discovery(http: OkHttpClient): org.idejo.app.core.api.DiscoveryClient =
        org.idejo.app.core.api
            .DiscoveryClient(http)

    @Provides
    @Singleton
    fun foreground(
        sessions: SessionManager,
        topics: InboxTopics,
        @AppScope scope: CoroutineScope,
    ): ForegroundSockets = ForegroundSockets(sessions, scope, InboxSockets(sessions, topics, scope))

    /** **Private Message** through the inbox's Chats (IB-07). */
    @Provides
    @Singleton
    fun inboxChats(
        sessions: SessionManager,
        links: PendingLinks,
    ): InboxChats = InboxChats(sessions, links)

    /** Where the logo menu sends the member in the inbox (SH-12 to SH-14). */
    @Provides
    @Singleton
    fun inboxShortcuts(): InboxShortcuts = InboxShortcuts()

    /** The last place opened, restored on the next launch (MD-05). */
    @Provides
    @Singleton
    fun lastPlaces(
        sessions: SessionManager,
        store: ForumStore,
        @AppScope scope: CoroutineScope,
    ): LastPlaces = LastPlaces(sessions, store, scope)

    /** The text typed and not sent (OF-03), dropped with a forum the app lets go of. */
    @Provides
    @Singleton
    fun drafts(
        @ApplicationContext context: Context,
        sessions: SessionManager,
        @AppScope scope: CoroutineScope,
    ): Drafts =
        Drafts(File(context.filesDir, "drafts")).also { drafts ->
            scope.launch { sessions.forgotten.collect { drafts.forget(it) } }
        }

    /** The address a tapped notification opens (NT-04). */
    @Provides
    @Singleton
    fun pendingLinks(): PendingLinks = PendingLinks()
}

private const val MEGABYTE = 1024L * 1024L
