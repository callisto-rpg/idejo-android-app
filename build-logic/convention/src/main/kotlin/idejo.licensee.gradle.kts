// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// The licence check of every dependency shipped (CLAUDE.md → Stack, spec §24): Apache-2.0,
// MIT, BSD-style only; anything else fails `check`. Nothing proprietary: the one build goes
// to Google Play, F-Droid and GitHub.
plugins {
    id("app.cash.licensee")
}

licensee {
    allow("Apache-2.0")
    allow("MIT")
    allow("BSD-2-Clause")
    allow("BSD-3-Clause")
}
