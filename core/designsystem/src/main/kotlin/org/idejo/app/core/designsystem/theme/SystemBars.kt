// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** Whether the system bars need dark icons on [background] (TH-09). */
fun systemBarsDarkIcons(background: Color): Boolean =
    contrastRatio(OnLight, background) >= contrastRatio(Color.White, background)

/**
 * Edge to edge, the system bars transparent over the page so they take its colour, with
 * icons that read on it (TH-09).
 */
@Composable
internal fun SystemBarsEffect(background: Color) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val darkIcons = systemBarsDarkIcons(background)
    LaunchedEffect(activity, darkIcons) {
        val transparent = Color.Transparent.toArgb()
        val style =
            if (darkIcons) {
                SystemBarStyle.light(transparent, transparent)
            } else {
                SystemBarStyle.dark(transparent)
            }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
