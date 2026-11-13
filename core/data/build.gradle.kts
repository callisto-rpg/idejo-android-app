// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// Repositories, the Room cache per forum, DataStore, Keystore, ForumRegistry, drafts.
plugins {
    id("idejo.android.library")
}

android {
    namespace = "org.idejo.app.core.data"
}

dependencies {
    api(project(":core:api"))
    api(project(":core:realtime"))
}
