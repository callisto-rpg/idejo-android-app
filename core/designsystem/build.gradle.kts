// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// IdejoTheme (TH-01 to TH-10), fonts and the shared components.
plugins {
    id("idejo.android.library")
    id("idejo.android.compose")
    id("idejo.android.screenshots")
}

android {
    namespace = "org.idejo.app.core.designsystem"
}

dependencies {
    // Components that draw the API's resources (topics, their dots).
    api(project(":core:model"))
    implementation(libs.androidx.activity.compose)
}
