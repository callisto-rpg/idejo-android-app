// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.theme

/** The member's **Theme** choice (TH-03), saved per account. */
enum class ThemeMode {
    System,
    Light,
    Dark,
    ;

    /** Whether to draw dark, given whether the system is dark now. */
    fun isDark(systemDark: Boolean): Boolean =
        when (this) {
            System -> systemDark
            Light -> false
            Dark -> true
        }
}
