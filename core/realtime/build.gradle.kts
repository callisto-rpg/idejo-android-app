// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// The Phoenix Channels client and idejo's events (pure JVM).
plugins {
    id("idejo.jvm.library")
    id("idejo.serialization")
}

dependencies {
    api(project(":core:api"))

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)
}
