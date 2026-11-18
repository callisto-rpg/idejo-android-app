// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

package org.idejo.app.feature.settings.parts

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** [at] as a date in the device's zone and language ("signed in on …", "Added …"). */
fun day(
    at: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(zone).format(at)
