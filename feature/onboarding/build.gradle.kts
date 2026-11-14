// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// The first launch: the welcome screen (MD-01), adding a forum by its address (SI-01, SI-02),
// signing in with a Custom Tab (SI-03) or reading as a visitor (SI-05).
plugins {
    id("idejo.android.feature")
    id("idejo.android.hilt")
    id("idejo.android.screenshots")
}

android {
    namespace = "org.idejo.app.feature.onboarding"
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.coil.compose)

    testImplementation(project(":core:testing"))
}
