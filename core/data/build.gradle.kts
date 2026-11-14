// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// The forums the app holds (ForumStore), their tokens sealed with a Keystore key
// (TokenVault, SI-04), one ForumSession per forum, and the SessionManager. The Room cache
// comes with OF-01 (M5; steward decision, 2026-11-14).
plugins {
    id("idejo.android.library")
    id("idejo.android.hilt")
    id("idejo.serialization")
}

android {
    namespace = "org.idejo.app.core.data"
}

dependencies {
    api(project(":core:api"))
    api(project(":core:realtime"))
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.lifecycle.process)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
