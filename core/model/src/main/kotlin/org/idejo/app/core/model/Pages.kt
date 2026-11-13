// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A list answer: `{"data": […]}`. */
@Serializable
data class ListBody<T>(
    val data: List<T> = emptyList(),
)

/** A page (API guide §4): cursors are opaque and signed; pass them back as they are. */
@Serializable
data class Page<T>(
    val data: List<T> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("prev_cursor") val prevCursor: String? = null,
)
