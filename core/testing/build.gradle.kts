// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// FakeIdejo: an in-process idejo for tests (NF-08), on MockWebServer (pure JVM). Only ever
// a test dependency.
plugins {
    id("idejo.jvm.library")
    id("idejo.serialization")
}

dependencies {
    api(project(":core:api"))
    api(libs.okhttp.mockwebserver)

    testImplementation(project(":core:realtime"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
