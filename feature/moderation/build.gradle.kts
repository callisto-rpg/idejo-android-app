// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// Moderation (MO, milestone M6).
plugins {
    id("idejo.android.feature")
}

android {
    namespace = "org.idejo.app.feature.moderation"
}

dependencies {
    implementation(project(":core:data"))
}
