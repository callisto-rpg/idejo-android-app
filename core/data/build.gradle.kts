// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// The forums the app holds (ForumStore), their tokens sealed with a Keystore key
// (TokenVault, SI-04), one ForumSession per forum, the SessionManager, and the Room cache
// of what each forum last answered (OF-01).
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
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
}
