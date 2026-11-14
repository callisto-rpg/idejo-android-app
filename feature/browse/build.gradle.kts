// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// Home, category, tags, search, unread, rules, help (BR).
plugins {
    id("idejo.android.feature")
    id("idejo.android.hilt")
    id("idejo.android.screenshots")
}

android {
    namespace = "org.idejo.app.feature.browse"
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.coil.compose)

    testImplementation(project(":core:testing"))
}
