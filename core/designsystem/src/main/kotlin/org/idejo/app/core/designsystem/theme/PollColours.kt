// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.theme

/**
 * The bar colours a poll's answer may take from the app (CO-13), as `#rrggbb`, the form the
 * server stores. The web offers any colour; these are a member's choice, not the theme's.
 * No colour at all: the theme's primary colour.
 */
val PollBarColours: List<String> =
    listOf(
        "#e53935",
        "#fb8c00",
        "#fdd835",
        "#43a047",
        "#00897b",
        "#1e88e5",
        "#8e24aa",
        "#d81b60",
        "#6d4c41",
        "#757575",
    )
