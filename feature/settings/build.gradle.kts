// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// Profile and account, app settings (ST). In M1, App settings shows the version and the
// licences only (steward decision, 2026-11-14); the rest comes with M3.
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
    implementation(libs.androidx.hilt.viewmodel.compose)

    testImplementation(project(":core:testing"))
}
