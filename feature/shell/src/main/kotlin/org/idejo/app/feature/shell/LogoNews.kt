// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.shell

import org.idejo.app.core.designsystem.component.LogoNews
import org.idejo.app.core.model.MentionState
import kotlin.time.Duration.Companion.minutes

/** How often the logo's dot reads the other forums again while one is open (SH-03). */
internal val NEWS_EVERY = 5.minutes

/**
 * The logo's dot (SH-03) from each forum's strongest news (`InboxTopicsState.levels`, IB-04),
 * leaving out the forum [here]: red when a message mentions the member or one of their roles,
 * as the web counts `mentions`; white for other new messages.
 */
fun logoNews(
    levels: Map<String, MentionState>,
    here: String,
): LogoNews? {
    val others = levels.filterKeys { it != here }.values
    return when {
        others.any { it == MentionState.Mention || it == MentionState.Role } -> LogoNews.Mention
        others.any { it != MentionState.None } -> LogoNews.NewMessages
        else -> null
    }
}
