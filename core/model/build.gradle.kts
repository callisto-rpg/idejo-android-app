// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// API resources as Kotlin data classes (pure JVM), read leniently (SY-07).
plugins {
    id("idejo.jvm.library")
    id("idejo.serialization")
}

dependencies {
    api(libs.kotlinx.serialization.json)
}
