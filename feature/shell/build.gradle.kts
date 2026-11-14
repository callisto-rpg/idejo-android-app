// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// The forum frame, logo menu, user menu and sidebars (SH, RS).
plugins {
    id("idejo.android.feature")
}

android {
    namespace = "org.idejo.app.feature.shell"
}

dependencies {
    implementation(project(":core:data"))
}
