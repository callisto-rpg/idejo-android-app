// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// In-app bubbles (in the shell), UnifiedPush and the notifications it shows (NT).
plugins {
    id("idejo.android.feature")
    id("idejo.serialization")
    id("idejo.android.hilt")
}

android {
    namespace = "org.idejo.app.feature.notifications"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    implementation(project(":core:api"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.unifiedpush.connector)
    implementation(libs.coil.compose)

    testImplementation(project(":core:testing"))
    testImplementation(libs.robolectric)
}
