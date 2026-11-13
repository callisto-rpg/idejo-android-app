// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.api

import kotlinx.serialization.json.Json

/**
 * How every API answer and event is read (SY-07, API guide §10): unknown fields ignored,
 * unknown enum values and nulls where a default exists read as that default. Requests carry
 * only what differs from the defaults, as in the guide's examples.
 */
val IdejoJson: Json =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = false
    }
