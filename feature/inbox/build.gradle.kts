// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
    id("idejo.android.feature")
    id("idejo.android.hilt")
    id("idejo.android.screenshots")
}

android {
    namespace = "org.idejo.app.feature.inbox"
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)

    testImplementation(project(":core:testing"))
}
