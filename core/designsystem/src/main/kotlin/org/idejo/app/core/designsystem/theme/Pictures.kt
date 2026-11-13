// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.theme

/**
 * The picture to show for someone (TH-10): their dark-theme picture (`avatar_dark_url`) in
 * the dark theme when they have one, else their picture, as the web does.
 */
fun pictureUrl(
    avatarUrl: String?,
    avatarDarkUrl: String?,
    dark: Boolean,
): String? = if (dark && !avatarDarkUrl.isNullOrBlank()) avatarDarkUrl else avatarUrl
