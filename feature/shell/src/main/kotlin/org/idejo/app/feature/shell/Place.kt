// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import kotlinx.serialization.Serializable

/**
 * Where the member is in a forum: what the breadcrumbs show (SH-02) and what the sidebar
 * highlights (SH-34). The app's navigation uses these as its typed routes.
 */
@Serializable
sealed interface Place {
    @Serializable
    data object Home : Place

    @Serializable
    data class Category(
        val id: String,
    ) : Place

    @Serializable
    data class Topic(
        val id: String,
    ) : Place

    @Serializable
    data object Unread : Place

    @Serializable
    data object Tags : Place

    @Serializable
    data object Search : Place

    @Serializable
    data object Rules : Place

    @Serializable
    data object Help : Place

    @Serializable
    data object AppSettings : Place
}
