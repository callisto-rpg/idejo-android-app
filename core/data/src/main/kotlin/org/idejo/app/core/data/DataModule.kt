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
    ): SessionManager = SessionManager(store, vault, http, scope, Dispatchers.Default)

    @Provides
    @Singleton
    fun foreground(
        sessions: SessionManager,
        @AppScope scope: CoroutineScope,
    ): ForegroundSockets = ForegroundSockets(sessions, scope)
}
