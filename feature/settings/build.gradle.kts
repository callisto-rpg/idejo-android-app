// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// Profile and account, app settings (ST).
plugins {
    id("idejo.android.feature")
}

android {
    namespace = "org.idejo.app.feature.settings"
}

dependencies {
    implementation(project(":core:data"))
}
