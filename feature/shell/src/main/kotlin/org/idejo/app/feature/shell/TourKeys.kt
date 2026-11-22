// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** A key's name, marked to be drawn as a key by [withKeys]. */
internal fun key(label: String): String = "$KEY_START$label$KEY_END"

/** [text] with each [key] drawn as a key: monospace, bold, on a tint. */
@Composable
internal fun withKeys(text: String): AnnotatedString {
    val tint = MaterialTheme.colorScheme.surfaceVariant
    return buildAnnotatedString {
        var at = 0
        while (at < text.length) {
            val start = text.indexOf(KEY_START, at)
            val end = if (start < 0) -1 else text.indexOf(KEY_END, start)
            if (start < 0 || end < 0) {
                append(text.substring(at).replace(KEY_START.toString(), ""))
                break
            }
            append(text.substring(at, start))
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    background = tint,
                ),
            ) { append(" ${text.substring(start + 1, end)} ") }
            at = end + 1
        }
    }
}

private const val KEY_START = '\u0001'
private const val KEY_END = '\u0002'
