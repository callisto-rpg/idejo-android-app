// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.idejo.app.core.data.UserAgent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** `idejo-android/<version>`: no device or member details (SY-05). */
    @Provides
    @UserAgent
    fun userAgent(
        @ApplicationContext context: Context,
    ): String {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        return "idejo-android/${version ?: "0"}"
    }
}
