// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// Forums, Topics and Chats (IB), private conversations (PC).
plugins {
    id("idejo.android.feature")
}

android {
    namespace = "org.idejo.app.feature.inbox"
}

dependencies {
    implementation(project(":core:data"))
}
