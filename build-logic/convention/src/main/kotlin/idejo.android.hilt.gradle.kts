// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// Hilt with KSP.
plugins {
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

dependencies {
    "implementation"(catalog.library("hilt-android"))
    "ksp"(catalog.library("hilt-compiler"))
}
