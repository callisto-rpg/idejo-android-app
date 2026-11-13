// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "idejo-android"

// The module layout of CLAUDE.md → Architecture.
include(":app")
include(":core:model", ":core:api", ":core:realtime")
include(":core:data", ":core:markdown", ":core:designsystem")
include(
    ":feature:onboarding",
    ":feature:shell",
    ":feature:browse",
    ":feature:topic",
    ":feature:composer",
    ":feature:inbox",
    ":feature:notifications",
    ":feature:settings",
    ":feature:moderation",
)
