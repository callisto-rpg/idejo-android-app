// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// An Android library module (core/data, core/markdown, core/designsystem, features).
plugins {
    id("com.android.library")
}

android {
    compileSdk = catalog.int("sdk-compile")
    defaultConfig {
        minSdk = catalog.int("sdk-min")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(catalog.version("jvm"))
        targetCompatibility = JavaVersion.toVersion(catalog.version("jvm"))
    }
    lint {
        targetSdk = catalog.int("sdk-target")
        idejoDefaults()
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(catalog.int("jvm"))
}

dependencies {
    "testImplementation"(catalog.library("junit"))
}
