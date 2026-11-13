// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// In-app bubbles, UnifiedPush, notification channels (NT).
plugins {
    id("idejo.android.feature")
}

android {
    namespace = "org.idejo.app.feature.notifications"
}

dependencies {
    implementation(project(":core:data"))
}
