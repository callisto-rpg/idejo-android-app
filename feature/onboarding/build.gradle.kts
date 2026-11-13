// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// Welcome, add a forum, sign-in, connect forums (MD, SI).
plugins {
    id("idejo.android.feature")
}

android {
    namespace = "org.idejo.app.feature.onboarding"
}

dependencies {
    implementation(project(":core:data"))
}
