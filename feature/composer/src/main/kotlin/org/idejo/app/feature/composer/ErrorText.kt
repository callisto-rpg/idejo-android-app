// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.composer

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/** What went wrong, in the server's words when it gave them. */
@Composable
internal fun errorText(error: ComposerError): String =
    when (error) {
        is ComposerError.Refused -> {
            val seconds = error.retryAfter?.seconds?.toInt()
            listOfNotNull(
                error.error.message.ifBlank { null },
                seconds?.let { pluralStringResource(R.plurals.composer_retry_after, it, it) },
            ).joinToString(" ")
        }

        ComposerError.Unreachable -> {
            stringResource(R.string.composer_unreachable)
        }

        ComposerError.NothingToHide -> {
            stringResource(R.string.composer_nothing_to_hide)
        }

        ComposerError.ImageRefused -> {
            stringResource(R.string.composer_image_refused)
        }
    }
