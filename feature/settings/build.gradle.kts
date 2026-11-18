// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// Profile and account (ST, per forum: one folder per section, CLAUDE.md → "Adding a settings
// section") and App settings (ST-20 to ST-23, on this device).
plugins {
    id("idejo.android.feature")
    id("idejo.android.screenshots")
    id("idejo.serialization")
    id("idejo.android.hilt")
}

android {
    namespace = "org.idejo.app.feature.settings"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:markdown"))
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.coil.compose)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
}
