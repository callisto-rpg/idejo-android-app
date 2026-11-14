// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// Home, category, tags, search, unread, rules, help (BR).
plugins {
    id("idejo.android.feature")
}

android {
    namespace = "org.idejo.app.feature.browse"
}

dependencies {
    implementation(project(":core:data"))
}
