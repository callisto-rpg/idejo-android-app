// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// The composer state machine, editor and commands (CO).
plugins {
    id("idejo.android.feature")
    id("idejo.android.hilt")
    id("idejo.android.screenshots")
}

android {
    namespace = "org.idejo.app.feature.composer"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:markdown"))
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.coil.compose)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
}
