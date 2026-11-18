// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** App settings' cache (ST-21): the app's cache folder. */
@Module
@InstallIn(SingletonComponent::class)
interface SettingsModule {
    @Binds
    fun cache(folder: AppCacheFolder): AppCache
}
