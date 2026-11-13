// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// A plain Kotlin/JVM module: no Android imports, fast unit tests (core/model, core/api,
// core/realtime; CLAUDE.md → Architecture).
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(catalog.int("jvm"))
}

dependencies {
    "testImplementation"(catalog.library("junit"))
}
