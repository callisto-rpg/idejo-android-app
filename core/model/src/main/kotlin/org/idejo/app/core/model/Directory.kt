// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A forum in the group's directory (SI-08): `GET <directory_url>api/v1/forums`. */
@Serializable
data class DirectoryForum(
    val name: String,
    val url: String,
    val host: String,
    val description: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    val members: Int = 0,
)

/** One page of the directory: 20 forums, [page] of [pages]. */
@Serializable
data class DirectoryPage(
    val forums: List<DirectoryForum> = emptyList(),
    val page: Int = 1,
    val pages: Int = 1,
    val total: Int = 0,
) {
    val more: Boolean get() = page < pages
}
