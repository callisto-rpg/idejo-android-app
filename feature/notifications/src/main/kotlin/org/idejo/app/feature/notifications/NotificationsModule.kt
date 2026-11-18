// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.notifications

import android.content.Context
import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import org.idejo.app.core.data.AppScope
import org.idejo.app.core.data.PushRegistrar
import org.idejo.app.core.data.SessionManager
import javax.inject.Singleton

/** Push (NT-02): the connector's distributor and the registrar every forum goes through. */
@Module
@InstallIn(SingletonComponent::class)
object NotificationsModule {
    @Provides
    @Singleton
    fun distributor(
        @ApplicationContext context: Context,
    ): UnifiedPushDistributor = UnifiedPushDistributor(context)

    @Provides
    @Singleton
    fun registrar(
        sessions: SessionManager,
        distributor: UnifiedPushDistributor,
        @AppScope scope: CoroutineScope,
    ): PushRegistrar = PushRegistrar(sessions, distributor, deviceName(), scope).also { it.start() }

    /** As at sign-in: the phone's model, which the member sees in Apps and devices. */
    private fun deviceName(): String = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android"
}
