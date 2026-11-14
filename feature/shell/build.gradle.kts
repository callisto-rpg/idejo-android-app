// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// The forum frame: top bar (SH-01, SH-02), logo menu (SH-10, SH-11, SH-14), user menu
// (SH-20 to SH-25) and the left sidebar (SH-30 to SH-38). The right sidebar comes in M7 (RS).
plugins {
    id("idejo.android.feature")
    id("idejo.android.hilt")
    id("idejo.android.screenshots")
    id("idejo.serialization")
}

android {
    namespace = "org.idejo.app.feature.shell"
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.coil.compose)

    testImplementation(project(":core:testing"))
}
