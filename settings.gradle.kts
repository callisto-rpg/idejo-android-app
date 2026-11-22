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
        // Robolectric's Android jar (140 MB) from Google's mirror of Maven Central: Maven
        // Central answers 429 to busy CI runners for it, and Gradle does not fall through
        // to another repository on a 429.
        exclusiveContent {
            forRepository {
                maven("https://maven-central.storage-download.googleapis.com/maven2/") {
                    name = "MavenCentralGoogleMirror"
                }
            }
            filter { includeModule("org.robolectric", "android-all-instrumented") }
        }
        mavenCentral()
    }
}

rootProject.name = "idejo-android"

// The module layout of CLAUDE.md → Architecture.
include(":app")
include(":core:model", ":core:api", ":core:realtime")
include(":core:data", ":core:markdown", ":core:designsystem")
// The fake idejo server, for tests only (NF-08); never a dependency of the app.
include(":core:testing")
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
