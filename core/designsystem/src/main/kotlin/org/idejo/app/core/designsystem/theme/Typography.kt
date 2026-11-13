// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.idejo.app.core.designsystem.R

// Fraunces for titles, Rubik for text (TH-04), both variable fonts bundled from their
// upstream releases (OFL-1.1). Sizes are in sp, so the system font scale applies.

/**
 * Fraunces at the optical size [opticalSize] (its `opsz` axis, as the web sets it from the
 * text size), without the "soft" and "wonky" alternates, as idejo's web shows it.
 */
private fun fraunces(opticalSize: Float): FontFamily {
    fun face(weight: FontWeight) =
        Font(
            R.font.fraunces,
            weight,
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(weight.weight),
                    FontVariation.Setting("opsz", opticalSize),
                    FontVariation.Setting("SOFT", 0f),
                    FontVariation.Setting("WONK", 0f),
                ),
        )
    return FontFamily(face(FontWeight.SemiBold), face(FontWeight.Bold))
}

/** Rubik, the text face, in the weights idejo uses. */
val Rubik: FontFamily =
    FontFamily(
        listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold).map {
            Font(
                R.font.rubik,
                it,
                variationSettings = FontVariation.Settings(FontVariation.weight(it.weight)),
            )
        },
    )

private val titleFamilies = mutableSetOf<FontFamily>()

/** Whether [family] is one of the Fraunces families of [IdejoTypography] (TH-04). */
fun isFraunces(family: FontFamily?): Boolean = family in titleFamilies

private fun title(
    size: TextUnit,
    lineHeight: TextUnit,
    weight: FontWeight = FontWeight.SemiBold,
): TextStyle =
    TextStyle(
        fontFamily = fraunces(size.value).also { titleFamilies += it },
        fontWeight = weight,
        fontSize = size,
        lineHeight = lineHeight,
    )

private fun text(
    size: TextUnit,
    lineHeight: TextUnit,
    weight: FontWeight = FontWeight.Normal,
): TextStyle =
    TextStyle(fontFamily = Rubik, fontWeight = weight, fontSize = size, lineHeight = lineHeight)

/**
 * idejo's type scale: display, headline and title in Fraunces (a topic's title and the
 * breadcrumbs are `titleMedium`, 17 sp), body and label in Rubik (a message is
 * `bodyMedium`, 15 sp; the sidebar's rows `bodyLarge`, 17 sp, SH-38).
 */
val IdejoTypography: Typography =
    Typography(
        displayLarge = title(57.sp, 64.sp, FontWeight.Bold),
        displayMedium = title(45.sp, 52.sp, FontWeight.Bold),
        displaySmall = title(38.sp, 42.sp, FontWeight.Bold),
        headlineLarge = title(32.sp, 40.sp),
        headlineMedium = title(28.sp, 36.sp),
        headlineSmall = title(24.sp, 32.sp),
        titleLarge = title(21.sp, 28.sp),
        titleMedium = title(17.sp, 24.sp),
        titleSmall = title(15.sp, 20.sp),
        bodyLarge = text(17.sp, 24.sp),
        bodyMedium = text(15.sp, 22.sp),
        bodySmall = text(13.sp, 18.sp),
        labelLarge = text(14.sp, 20.sp, FontWeight.Medium),
        labelMedium = text(12.sp, 16.sp, FontWeight.Medium),
        labelSmall = text(11.sp, 16.sp, FontWeight.SemiBold),
    )
