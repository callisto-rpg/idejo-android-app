// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// The composer state machine, editor and commands (CO).
plugins {
    id("idejo.android.feature")
}

android {
    namespace = "org.idejo.app.feature.composer"
}

dependencies {
    implementation(project(":core:data"))
}
