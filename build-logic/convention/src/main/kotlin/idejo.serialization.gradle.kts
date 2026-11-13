// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// kotlinx.serialization for API resources and events, read leniently (SY-07).
plugins {
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    "implementation"(catalog.library("kotlinx-serialization-json"))
}
