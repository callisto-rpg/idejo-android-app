// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.core.testing

import java.time.Duration
import java.time.Instant

/** The fake server's clock: time moves only when a test says so. */
class FakeClock(
    @Volatile var now: Instant = Instant.parse("2026-11-14T09:40:00Z"),
) {
    fun advance(by: Duration) {
        now += by
    }

    fun advanceSeconds(seconds: Long) = advance(Duration.ofSeconds(seconds))
}
