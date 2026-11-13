// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// Compose for an Android module (applied after idejo.android.library or the application).
plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

extensions.configure<com.android.build.api.dsl.CommonExtension>("android") {
    buildFeatures.compose = true
}

dependencies {
    val bom = platform(catalog.library("compose-bom"))
    "implementation"(bom)
    "implementation"(catalog.library("compose-ui"))
    "implementation"(catalog.library("compose-material3"))
    "implementation"(catalog.library("compose-ui-tooling-preview"))
    // UI states are StateFlows collected with the lifecycle (CLAUDE.md → Architecture); also
    // lifts Compose's older lifecycle-runtime, whose lint checks today's Lint cannot load.
    "implementation"(catalog.library("androidx-lifecycle-runtime-compose"))
    "debugImplementation"(catalog.library("compose-ui-tooling"))
}
