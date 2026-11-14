// SPDX-FileCopyrightText: 2026 Mariano Pictet and contributors
// SPDX-License-Identifier: Apache-2.0

// idejo's Markdown dialect: parser and Compose renderer (TV-20 to TV-24).
plugins {
    id("idejo.android.library")
    id("idejo.android.compose")
}

android {
    namespace = "org.idejo.app.core.markdown"
}

dependencies {
    api(project(":core:model"))
}
