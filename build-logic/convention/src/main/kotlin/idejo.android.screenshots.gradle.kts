// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// Screenshot tests with Roborazzi on Robolectric (CLAUDE.md → Stack). The images are kept
// in src/test/screenshots: `recordRoborazziDebug` writes them, `verifyRoborazziDebug`
// compares against them.
plugins {
    id("io.github.takahirom.roborazzi")
}

extensions.configure<com.android.build.api.dsl.LibraryExtension>("android") {
    testOptions.unitTests.isIncludeAndroidResources = true
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    "testImplementation"(catalog.library("robolectric"))
    "testImplementation"(catalog.library("roborazzi"))
    "testImplementation"(catalog.library("roborazzi-compose"))
    "testImplementation"(platform(catalog.library("compose-bom")))
    "testImplementation"(catalog.library("compose-ui-test-junit4"))
    "debugImplementation"(catalog.library("compose-ui-test-manifest"))
}
