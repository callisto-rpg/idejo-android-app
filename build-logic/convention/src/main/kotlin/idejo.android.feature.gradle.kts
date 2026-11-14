// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// A feature module: an Android library with Compose. Features depend on core modules,
// never on each other (CLAUDE.md → Architecture).
plugins {
    id("idejo.android.library")
    id("idejo.android.compose")
}

dependencies {
    "implementation"(project(":core:designsystem"))
}
