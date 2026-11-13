// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** How long switching forum cross-fades the colours (TH-07): none with reduced motion. */
fun themeFadeMillis(reducedMotion: Boolean): Int = if (reducedMotion) 0 else THEME_FADE_MILLIS

/** Whether the member turned animations off in Android's settings (AX-05). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) ==
            0f
    }
}

/** Reduced motion, for glows, pulses and fades (AX-05); `IdejoTheme.reducedMotion`. */
val LocalReducedMotion = staticCompositionLocalOf { false }

private const val THEME_FADE_MILLIS = 300
