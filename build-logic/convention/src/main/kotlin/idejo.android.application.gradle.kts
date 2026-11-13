// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// The app module: org.idejo.app for both flavours, ".debug" for debug builds.
plugins {
    id("com.android.application")
}

android {
    compileSdk = catalog.int("sdk-compile")
    defaultConfig {
        minSdk = catalog.int("sdk-min")
        targetSdk = catalog.int("sdk-target")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(catalog.version("jvm"))
        targetCompatibility = JavaVersion.toVersion(catalog.version("jvm"))
    }
    lint {
        idejoDefaults()
    }
}

kotlin {
    jvmToolchain(catalog.int("jvm"))
}

dependencies {
    "testImplementation"(catalog.library("junit"))
}
