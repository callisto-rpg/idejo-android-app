// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// The topic page, messages, jump card, live updates (TV, MA).
plugins {
    id("idejo.android.feature")
}

android {
    namespace = "org.idejo.app.feature.topic"
}

dependencies {
    implementation(project(":core:data"))
}
